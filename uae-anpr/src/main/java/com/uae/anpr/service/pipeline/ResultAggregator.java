package com.uae.anpr.service.pipeline;

import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.parser.UaePlateParser;
import com.uae.anpr.service.parser.UaePlateParser.PlateBreakdown;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aggregates OCR hypotheses originating from multiple preprocessing strategies. The aggregator
 * boosts candidates that repeatedly appear across variants and that structurally resemble a UAE
 * licence plate while penalising inconsistent readings. This ensemble-style voting stabilises the
 * output when Tesseract confuses similar glyphs such as {@code P} and {@code 9}.
 */
@Component
public class ResultAggregator {

    private static final Logger log = LoggerFactory.getLogger(ResultAggregator.class);

    private final UaePlateParser parser;

    public ResultAggregator(UaePlateParser parser) {
        this.parser = parser;
    }

    public Optional<AggregatedResult> selectBest(List<OcrResult> results, double threshold) {
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }

        Map<String, CandidateHypothesis> hypotheses = new LinkedHashMap<>();
        for (OcrResult result : results) {
            if (result == null || result.text() == null || result.text().isBlank()) {
                continue;
            }
            String normalized = result.text().toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (normalized.isBlank()) {
                continue;
            }
            CandidateHypothesis hypothesis =
                    hypotheses.computeIfAbsent(normalized, key -> new CandidateHypothesis(key, parser.parse(key)));
            hypothesis.observe(result.confidence());
        }

        List<AggregatedResult> candidates = hypotheses.values().stream()
                .map(CandidateHypothesis::finalizeResult)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Comparator<AggregatedResult> byScore = Comparator.comparingDouble(AggregatedResult::score);
        Optional<AggregatedResult> confident = candidates.stream()
                .filter(candidate -> candidate.confidence() >= threshold)
                .max(byScore);

        Optional<AggregatedResult> best = confident.isPresent()
                ? confident
                : candidates.stream().max(byScore);

        best.ifPresent(candidate -> {
            boolean meetsThreshold = candidate.confidence() >= threshold;
            log.debug(
                    "Aggregated candidate {} with confidence {} (occurrences: {}, city: {}, class: {}, digits: {}, meetsThreshold: {})",
                    candidate.text(),
                    candidate.confidence(),
                    candidate.occurrences(),
                    candidate.breakdown().city(),
                    candidate.breakdown().plateCharacter(),
                    candidate.breakdown().carNumber(),
                    meetsThreshold);
        });

        return best;
    }

    public record AggregatedResult(String text, double confidence, double score, int occurrences,
            PlateBreakdown breakdown) {
    }

    private static final class CandidateHypothesis {

        private final String text;
        private final PlateBreakdown breakdown;
        private int occurrences;
        private double maxConfidence;
        private double sumConfidence;

        private CandidateHypothesis(String text, PlateBreakdown breakdown) {
            this.text = text;
            this.breakdown = breakdown == null ? PlateBreakdown.empty() : breakdown;
        }

        private void observe(double confidence) {
            occurrences++;
            sumConfidence += Math.max(0.0, confidence);
            maxConfidence = Math.max(maxConfidence, Math.max(0.0, confidence));
        }

        private String text() {
            return text;
        }

        private PlateBreakdown breakdown() {
            return breakdown;
        }

        private int occurrences() {
            return occurrences;
        }

        private double maxConfidence() {
            return maxConfidence;
        }

        private double averageConfidence() {
            if (occurrences == 0) {
                return 0.0;
            }
            return sumConfidence / occurrences;
        }

        private Optional<AggregatedResult> finalizeResult() {
            if (occurrences == 0) {
                return Optional.empty();
            }
            double aggregatedConfidence = clamp(computeAggregatedConfidence());
            double score = aggregatedConfidence + consensusBonus() + structurePreference();
            return Optional.of(new AggregatedResult(text, aggregatedConfidence, score, occurrences, breakdown));
        }

        private double computeAggregatedConfidence() {
            double consensusBoost = Math.min(0.12, 0.045 * Math.max(0, occurrences - 1));
            double structureBoost = structureBoost();
            double penalty = structurePenalty();
            double stability = Math.min(maxConfidence, sumConfidence / occurrences);
            double aggregated = maxConfidence + consensusBoost + structureBoost - penalty;
            aggregated = Math.max(aggregated, stability);
            return aggregated;
        }

        private double structureBoost() {
            double boost = 0.0;
            String digits = breakdown.carNumber();
            if (digits != null && !digits.isBlank()) {
                int length = digits.length();
                if (length >= 4 && length <= 6) {
                    boost += 0.05;
                } else if (length >= 3 && length <= 7) {
                    boost += 0.02;
                }
            }
            String classification = breakdown.plateCharacter();
            if (classification != null && !classification.isBlank() && classification.length() <= 2) {
                boost += 0.02;
            }
            if (breakdown.city() != null) {
                boost += 0.01;
            }
            if (containsLetters() && containsDigits()) {
                boost += 0.01;
            }
            return boost;
        }

        private double structurePenalty() {
            double penalty = 0.0;
            String digits = breakdown.carNumber();
            if (digits == null || digits.length() < 3) {
                penalty += 0.06;
            } else if (digits.length() > 7) {
                penalty += 0.04;
            }
            if (!containsDigits()) {
                penalty += 0.08;
            }
            String classification = breakdown.plateCharacter();
            if (classification == null || classification.isBlank()) {
                penalty += 0.07;
            }
            if (!containsLetters()) {
                penalty += 0.08;
            }
            if (text.length() < 4) {
                penalty += 0.04;
            }
            return penalty;
        }

        private double structurePreference() {
            double preference = 0.0;
            String classification = breakdown.plateCharacter();
            if (classification != null && !classification.isBlank()) {
                preference += 0.05;
                if (classification.length() <= 2) {
                    preference += 0.01;
                }
            } else {
                preference -= 0.04;
            }
            if (containsLetters()) {
                preference += 0.01;
            } else {
                preference -= 0.03;
            }
            return preference;
        }

        private boolean containsDigits() {
            for (int i = 0; i < text.length(); i++) {
                if (Character.isDigit(text.charAt(i))) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsLetters() {
            for (int i = 0; i < text.length(); i++) {
                if (Character.isLetter(text.charAt(i))) {
                    return true;
                }
            }
            return false;
        }

        private double consensusBonus() {
            return Math.min(0.05, 0.01 * Math.max(0, occurrences - 1));
        }

        private double clamp(double value) {
            return Math.max(0.0, Math.min(0.999, value));
        }
    }
}
