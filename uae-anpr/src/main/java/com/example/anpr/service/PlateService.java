package com.example.anpr.service;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.dto.PlateResult;
import com.example.anpr.util.EmirateParser;
import com.example.anpr.util.ImageUtils;
import org.bytedeco.javacpp.indexer.UByteRawIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlateService {
    private static final Logger log = LoggerFactory.getLogger(PlateService.class);

    private final OcrService ocr;

    public PlateService(OcrService ocr) {
        this.ocr = ocr;
    }

    public PlateResponse recognize(byte[] imageBytes) {
        try {
            Mat src = ImageUtils.readMat(imageBytes);

            if (src.empty()) {
                return PlateResponse.of(new PlateResult("", "", "Unknown", "Invalid image"));
            }

            log.info("Processing image: {}x{}, channels: {}", src.cols(), src.rows(), src.channels());

            // For small images, use direct processing without complex ROI detection
            if (src.cols() <= 300 && src.rows() <= 100) {
                log.info("Small image detected, using direct processing");
                return processSmallImage(src);
            }

            // For larger images, try multiple strategies
            List<PlateResult> allResults = new ArrayList<>();

            // Strategy 0: Dubai style heuristic (runs first so it can win quickly when confident)
            PlateResult dubaiStyle = recognizeDubaiStylePlate(src);
            if (dubaiStyle != null) {
                allResults.add(dubaiStyle);
            }

            // Strategy 1: Original image with ROI detection
            PlateResult result1 = recognizeFrom(src, true, "ROI_DETECTION");
            allResults.add(result1);

            // Strategy 2: Full image without ROI
            PlateResult result2 = recognizeFrom(src, false, "FULL_IMAGE");
            allResults.add(result2);

            // Strategy 3: Enhanced image
            Mat enhanced = ImageUtils.preprocessUaePlate(src);
            PlateResult result3 = recognizeFrom(enhanced, false, "ENHANCED");
            allResults.add(result3);

            // Select the best result
            PlateResult bestResult = allResults.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingInt(this::confidence))
                    .orElse(new PlateResult("", "", "Unknown", "No results"));

            log.info("Best result: number='{}', letter='{}', emirate='{}'",
                    bestResult.number(), bestResult.letter(), bestResult.emirate());

            return PlateResponse.of(bestResult);

        } catch (Exception e) {
            log.error("Error during plate recognition", e);
            return PlateResponse.of(new PlateResult("", "", "Unknown", "Error: " + e.getMessage()));
        }
    }

    private PlateResponse processSmallImage(Mat src) {
        try {
            // Direct processing for small images
            Mat enhanced = ImageUtils.enhanceUaePlateRegion(src);
            enhanced = ImageUtils.ensureHeight(enhanced, 80, 200);

            PlateResult result = analyzePlateSimple(enhanced);
            return PlateResponse.of(result);

        } catch (Exception e) {
            log.error("Error processing small image", e);
            return PlateResponse.of(new PlateResult("", "", "Unknown", "Small image processing failed"));
        }
    }

    private PlateResult recognizeFrom(Mat src, boolean tryRoi, String stageLabel) {
        try {
            Rect roi = tryRoi ? ImageUtils.tryFindPlateROI(src) : null;
            Mat plate = (roi != null) ? new Mat(src, roi).clone() : src.clone();

            // Enhanced plate preprocessing
            Mat enhancedPlate = ImageUtils.enhanceUaePlateRegion(plate);
            enhancedPlate = ImageUtils.ensureHeight(enhancedPlate, 120, 480);

            String stage = stageLabel + (roi != null ? "_CROP" : "_FULL");
            return analyzePlate(enhancedPlate, roi, stage);

        } catch (Exception e) {
            log.warn("Recognition failed for stage {}: {}", stageLabel, e.getMessage());
            return new PlateResult("", "", "Unknown", "Stage " + stageLabel + " failed");
        }
    }

    private PlateResult analyzePlate(Mat plate, Rect roi, String stage) {
        int W = plate.cols();
        int H = plate.rows();

        log.debug("Analyzing plate: {}x{}, stage: {}", W, H, stage);

        // Simple split for UAE plates (typically 30% left for emirate/letter, 70% right for digits)
        int splitPoint = (int) (W * 0.3);
        splitPoint = Math.max(20, Math.min(splitPoint, W - 40));

        Rect leftRect = new Rect(0, 0, splitPoint, H);
        Rect rightRect = new Rect(splitPoint, 0, W - splitPoint, H);

        Mat left = new Mat(plate, leftRect).clone();
        Mat right = new Mat(plate, rightRect).clone();

        // Extract components
        String digits = extractDigitsEnhanced(right);
        String emirate = extractEmirateEnhanced(left);
        String letter = extractLetterEnhanced(left);

        // Parse and validate results
        EmirateParser.Parsed parsed = parseAndValidate(emirate, digits, letter);

        String diagnostics = String.format(
                "STAGE=%s | ROI=%s | SPLIT=%d/%d | DIGITS=%s | LETTER=%s | EMIRATE=%s",
                stage,
                roi != null ? roi.width() + "x" + roi.height() : "NONE",
                splitPoint, W,
                digits.trim(),
                letter,
                emirate.trim()
        );

        return new PlateResult(parsed.number, parsed.letter, parsed.emirate, diagnostics);
    }

    private PlateResult recognizeDubaiStylePlate(Mat original) {
        try {
            Rect roi = ImageUtils.tryFindPlateROI(original);
            Mat plate = (roi != null ? new Mat(original, roi) : original).clone();

            List<Mat> searchLevels = ImageUtils.generateDubaiPlateLevels(plate, 10);
            if (searchLevels.isEmpty()) {
                return null;
            }

            PlateResult bestResult = null;
            int bestScore = -1;

            for (int level = 0; level < searchLevels.size(); level++) {
                Mat normalised = searchLevels.get(level);
                if (normalised == null || normalised.empty()) {
                    continue;
                }

                // Expected layout:
                // ┌───────────────┬─────┐
                // │  Emirate text │ Ltr │  <- top band (~38% height)
                // ├───────────────┴─────┤
                // │       Digits        │  <- bottom band
                // └─────────────────────┘
                Rect topBand = ImageUtils.relativeRect(normalised, 0.0, 0.0, 1.0, 0.38);
                Rect digitsBand = ImageUtils.relativeRect(normalised, 0.06, 0.38, 0.88, 0.58);
                Rect letterRect = ImageUtils.relativeRect(normalised, 0.72, 0.05, 0.25, 0.30);

                Mat top = new Mat(normalised, topBand).clone();
                Mat digitsRegion = new Mat(normalised, digitsBand).clone();
                Mat letterRegion = new Mat(normalised, letterRect).clone();

                // Digits : combine multiple aggressive preprocessings
                List<String> digitCandidates = new ArrayList<>();
                for (Mat m : ImageUtils.generateDigitVariants(digitsRegion)) {
                    digitCandidates.add(ocr.ocrDigits(ImageUtils.toBufferedImage(m)));
                }
                String digits = selectBestDigits(digitCandidates);

                // Letter : focus on single character area with multiple crops
                List<String> letterCandidates = new ArrayList<>();
                letterCandidates.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.prepareDubaiLetter(letterRegion)))));
                for (Mat variant : ImageUtils.generateDubaiLetterVariants(normalised)) {
                    letterCandidates.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(variant))));
                }
                String letter = majorityLetter(letterCandidates.toArray(new String[0]));

                // Emirate : use entire top band and whole plate for redundancy
                String emirateCombined = String.join(" ", Arrays.asList(
                        ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.prepareDubaiEmirate(top))),
                        ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.prepareDubaiEmirate(normalised)))
                ));

                EmirateParser.Parsed parsed = parseAndValidate(emirateCombined, digits, letter);

                String diagnostics = String.format(
                        "DUBAI_HEURISTIC | LEVEL=%d/%d | SRC=%dx%d | ROI=%s | DIGITS=%s | LETTER=%s | EMIRATE=%s",
                        level + 1,
                        searchLevels.size(),
                        original.cols(),
                        original.rows(),
                        roi != null ? roi.width() + "x" + roi.height() : "FULL",
                        digits,
                        letter,
                        emirateCombined
                );

                if (parsed != null && parsed.number != null && parsed.number.length() >= 4) {
                    PlateResult candidate = new PlateResult(parsed.number, parsed.letter, parsed.emirate, diagnostics);
                    int score = scoreDubaiCandidate(parsed, digits, letter, emirateCombined);
                    if (score > bestScore) {
                        bestScore = score;
                        bestResult = candidate;
                    }

                    if (bestScore >= 10) {
                        break;
                    }
                }
            }

            return bestResult;

        } catch (Exception e) {
            log.debug("Dubai heuristic failed: {}", e.getMessage());
            return null;
        }
    }


    private String extractDigitsEnhanced(Mat rightRegion) {
        List<String> digitResults = new ArrayList<>();

        // Multiple processing variations
        Mat gray = ImageUtils.toGray(rightRegion);

        // Variation 1: High contrast
        Mat highContrast = ImageUtils.createHighContrast(gray);
        digitResults.add(ocr.ocrDigits(ImageUtils.toBufferedImage(highContrast)));

        // Variation 2: Adaptive threshold
        Mat adaptive = ImageUtils.adaptive(gray);
        digitResults.add(ocr.ocrDigits(ImageUtils.toBufferedImage(adaptive)));

        // Variation 3: Enhanced for OCR
        Mat enhanced = ImageUtils.enhanceForOCR(gray);
        digitResults.add(ocr.ocrDigits(ImageUtils.toBufferedImage(enhanced)));

        return selectBestDigits(digitResults);
    }

    private String extractEmirateEnhanced(Mat leftRegion) {
        List<String> emirateResults = new ArrayList<>();

        Mat gray = ImageUtils.toGray(leftRegion);

        // Multiple processing variations
        emirateResults.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.createHighContrast(gray))));
        emirateResults.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.adaptive(gray))));
        emirateResults.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.enhanceForOCR(gray))));

        return String.join(" | ", emirateResults.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList()));
    }

    private String extractLetterEnhanced(Mat leftRegion) {
        List<String> letterResults = new ArrayList<>();

        Mat gray = ImageUtils.toGray(leftRegion);

        // Multiple processing variations
        letterResults.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.createHighContrast(gray)))));
        letterResults.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.adaptive(gray)))));
        letterResults.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.enhanceForOCR(gray)))));

        return majorityLetter(letterResults.toArray(new String[0]));
    }

    private EmirateParser.Parsed parseAndValidate(String emirateRaw, String digitsRaw, String letterRaw) {
        EmirateParser.Parsed parsed = EmirateParser.parse(emirateRaw + " " + digitsRaw + " " + letterRaw);

        // Enhanced emirate detection
        if ("Unknown".equals(parsed.emirate)) {
            parsed.emirate = enhancedEmirateDetection(emirateRaw);
        }

        // Clean and validate number
        String number = digitsRaw.replaceAll("\\D+", "");
        if (number.length() > 5) {
            number = number.substring(0, 5);
        }
        parsed.number = number.isBlank() ? parsed.number : number;

        // Validate letter
        if (letterRaw != null && letterRaw.length() == 1 && Character.isLetter(letterRaw.charAt(0))) {
            parsed.letter = letterRaw.toUpperCase();
        } else if (parsed.letter == null || parsed.letter.isBlank()) {
            parsed.letter = letterRaw;
        }

        return parsed;
    }

    private int scoreDubaiCandidate(EmirateParser.Parsed parsed, String digitsRaw, String letterRaw, String emirateRaw) {
        int score = 0;

        String parsedNumber = parsed != null && parsed.number != null ? parsed.number : "";
        String digitsClean = digitsRaw != null ? digitsRaw.replaceAll("\\D+", "") : "";

        if (!parsedNumber.isBlank()) {
            score += Math.min(5, parsedNumber.replaceAll("\\D+", "").length());
        } else if (!digitsClean.isBlank()) {
            score += Math.min(4, digitsClean.length());
        }

        String parsedLetter = parsed != null ? parsed.letter : null;
        if (parsedLetter != null && !parsedLetter.isBlank()) {
            score += 2;
        } else if (letterRaw != null && !letterRaw.isBlank()) {
            score += 1;
        }

        String parsedEmirate = parsed != null ? parsed.emirate : null;
        if (parsedEmirate != null && !parsedEmirate.isBlank() && !"Unknown".equalsIgnoreCase(parsedEmirate)) {
            score += 2;
        } else if (emirateRaw != null && emirateRaw.toUpperCase().contains("DUBAI")) {
            score += 1;
        }

        if (digitsClean.length() >= 5) {
            score += 1;
        }

        return score;
    }

    private String enhancedEmirateDetection(String emirateRaw) {
        String text = emirateRaw.toUpperCase();

        Map<String, String> emiratePatterns = new HashMap<>();
        emiratePatterns.put("DUBAI", "Dubai");
        emiratePatterns.put("دبي", "Dubai");
        emiratePatterns.put("DXB", "Dubai");
        emiratePatterns.put("ABU", "Abu Dhabi");
        emiratePatterns.put("ابو", "Abu Dhabi");
        emiratePatterns.put("SHAR", "Sharjah");
        emiratePatterns.put("الشار", "Sharjah");
        emiratePatterns.put("AJM", "Ajman");
        emiratePatterns.put("عجم", "Ajman");
        emiratePatterns.put("RAK", "Ras Al Khaimah");
        emiratePatterns.put("رأس", "Ras Al Khaimah");

        for (Map.Entry<String, String> entry : emiratePatterns.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "Unknown";
    }

    private String selectBestDigits(List<String> digitResults) {
        List<String> normalized = digitResults.stream()
                .filter(Objects::nonNull)
                .map(s -> s.replaceAll("\\D+", ""))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.length() > 5 ? s.substring(0, 5) : s)
                .collect(Collectors.toList());

        if (normalized.isEmpty()) {
            return "";
        }

        int targetLength = determineDigitLength(normalized);
        List<String> targetCandidates = normalized.stream()
                .filter(s -> s.length() == targetLength)
                .collect(Collectors.toList());

        List<String> pool = targetCandidates.isEmpty() ? normalized : targetCandidates;

        return pool.stream()
                .max(Comparator.comparingInt(candidate -> digitConsensusScore(candidate, normalized)))
                .orElse(normalized.get(0));
    }

    private int determineDigitLength(List<String> candidates) {
        Map<Integer, Long> lengthCounts = candidates.stream()
                .collect(Collectors.groupingBy(String::length, Collectors.counting()));

        return lengthCounts.entrySet().stream()
                .sorted((a, b) -> {
                    int countCompare = Long.compare(b.getValue(), a.getValue());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    int closenessA = Math.abs(5 - a.getKey());
                    int closenessB = Math.abs(5 - b.getKey());
                    if (closenessA != closenessB) {
                        return Integer.compare(closenessA, closenessB);
                    }
                    return Integer.compare(b.getKey(), a.getKey());
                })
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(5);
    }

    private int digitConsensusScore(String candidate, List<String> all) {
        int score = 0;
        for (String other : all) {
            if (candidate.equals(other)) {
                score += 15;
                continue;
            }

            int min = Math.min(candidate.length(), other.length());
            for (int i = 0; i < min; i++) {
                if (candidate.charAt(i) == other.charAt(i)) {
                    score += 3;
                }
            }

            if (candidate.endsWith(other) || other.endsWith(candidate)) {
                score += 5;
            } else if (candidate.startsWith(other) || other.startsWith(candidate)) {
                score += 4;
            }
        }

        int closeness = Math.max(0, 5 - Math.abs(5 - candidate.length()));
        score += closeness * 2;
        return score;
    }

    private int confidence(PlateResult result) {
        if (result == null) return -1;

        int score = 0;

        // Number score (0-5 points)
        if (result.number() != null && !result.number().isBlank()) {
            score += Math.min(5, result.number().replaceAll("\\D+", "").length());
        }

        // Letter score (0-2 points)
        if (result.letter() != null && !result.letter().isBlank() && result.letter().length() == 1) {
            score += 2;
        }

        // Emirate score (0-3 points)
        if (result.emirate() != null && !result.emirate().isBlank() && !"Unknown".equalsIgnoreCase(result.emirate())) {
            score += 3;
        }

        return score;
    }

    private static String normalizeLetter(String s) {
        if (s == null) return "";
        s = s.toUpperCase().trim();

        // Common OCR corrections
        s = s.replace('0', 'O')
                .replace('1', 'I')
                .replace('2', 'Z')
                .replace('5', 'S')
                .replace('8', 'B')
                .replace('6', 'G');

        // Remove non-letters and take first character
        s = s.replaceAll("[^A-Z]", "");
        return s.length() > 0 ? s.substring(0, 1) : "";
    }

    private static String majorityLetter(String... options) {
        Map<String, Integer> counts = new HashMap<>();
        for (String opt : options) {
            if (opt != null && !opt.isBlank()) {
                counts.put(opt, counts.getOrDefault(opt, 0) + 1);
            }
        }

        if (counts.isEmpty()) return "";

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();
    }



















    // Update the analyzePlateSimple method in PlateService.java:

    private PlateResult analyzePlateSimple(Mat plate) {
        int W = plate.cols();
        int H = plate.rows();

        // Adaptive split based on image characteristics
        int splitPoint = calculateAdaptiveSplit(plate);
        splitPoint = Math.max(30, Math.min(splitPoint, W - 80));

        Rect leftRect = new Rect(0, 0, splitPoint, H);
        Rect rightRect = new Rect(splitPoint, 0, W - splitPoint, H);

        Mat left = new Mat(plate, leftRect).clone();
        Mat right = new Mat(plate, rightRect).clone();

        // Enhanced extraction with multiple attempts
        String digits = extractDigitsSimple(right);
        String emirate = extractEmirateSimple(left);
        String letter = extractLetterSimple(left);

        // Use enhanced emirate parser with confidence
        EmirateParser.Parsed parsed = EmirateParser.parseWithConfidence(emirate);
        parsed.number = cleanDigits(digits);
        parsed.letter = cleanLetter(letter);

        // Fallback: if emirate still unknown, try to detect from the entire plate
        if ("Unknown".equals(parsed.emirate)) {
            String fullPlateText = ocr.ocrEmirate(ImageUtils.toBufferedImage(plate));
            EmirateParser.Parsed fullParsed = EmirateParser.parseWithConfidence(fullPlateText);
            if (!"Unknown".equals(fullParsed.emirate)) {
                parsed.emirate = fullParsed.emirate;
            }
        }

        String diagnostics = String.format(
                "SIMPLE | SPLIT=%d/%d | DIGITS_RAW=%s | DIGITS=%s | LETTER_RAW=%s | LETTER=%s | EMIRATE_RAW=%s | EMIRATE=%s",
                splitPoint, W, digits, parsed.number, letter, parsed.letter, emirate, parsed.emirate
        );

        return new PlateResult(parsed.number, parsed.letter, parsed.emirate, diagnostics);
    }

    private int calculateAdaptiveSplit(Mat plate) {
        Mat gray = ImageUtils.toGray(plate);
        int W = gray.cols();
        int H = gray.rows();

        // Method 1: Vertical projection to find the gap
        int[] projection = new int[W];
        try (UByteRawIndexer indexer = gray.createIndexer()) {
            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    projection[x] += (indexer.get(y, x) & 0xFF);
                }
            }
        }

        // Find the point with minimum projection in the middle third
        int start = W / 3;
        int end = 2 * W / 3;
        int minProjection = Integer.MAX_VALUE;
        int bestSplit = W * 2 / 5; // Default 40%

        for (int x = start; x < end; x++) {
            if (projection[x] < minProjection) {
                minProjection = projection[x];
                bestSplit = x;
            }
        }

        return bestSplit;
    }

    private String extractDigitsSimple(Mat rightRegion) {
        List<String> results = new ArrayList<>();

        Mat gray = ImageUtils.toGray(rightRegion);

        // Try multiple preprocessing techniques
        results.add(ocr.ocrDigits(ImageUtils.toBufferedImage(ImageUtils.createHighContrast(gray))));
        results.add(ocr.ocrDigits(ImageUtils.toBufferedImage(ImageUtils.adaptive(gray))));
        results.add(ocr.ocrDigits(ImageUtils.toBufferedImage(ImageUtils.enhanceForOCR(gray))));

        // Try with morphological operations to connect broken digits
        Mat morph = applyDigitMorphology(gray);
        results.add(ocr.ocrDigits(ImageUtils.toBufferedImage(morph)));

        return selectBestDigits(results);
    }

    private String extractEmirateSimple(Mat leftRegion) {
        List<String> results = new ArrayList<>();

        Mat gray = ImageUtils.toGray(leftRegion);

        // Multiple preprocessing variations for emirate text
        results.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.createHighContrast(gray))));
        results.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.adaptive(gray))));
        results.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(ImageUtils.enhanceForOCR(gray))));

        // Try with different thresholds for Arabic text
        Mat arabicEnhanced = enhanceForArabicText(gray);
        results.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(arabicEnhanced)));

        // Try inverted
        Mat inverted = ImageUtils.invertImage(ImageUtils.createHighContrast(gray));
        results.add(ocr.ocrEmirate(ImageUtils.toBufferedImage(inverted)));

        return String.join(" | ", results.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList()));
    }

    private String extractLetterSimple(Mat leftRegion) {
        List<String> results = new ArrayList<>();

        Mat gray = ImageUtils.toGray(leftRegion);
        int H = gray.rows();
        int W = gray.cols();

        // Focus on the right part of the left region where the letter usually is
        int letterStartX = W * 2 / 3;
        if (letterStartX < W - 10) {
            Rect letterRect = new Rect(letterStartX, H / 4, W - letterStartX - 5, H / 2);
            Mat letterRegion = new Mat(gray, letterRect);

            results.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.createHighContrast(letterRegion)))));
            results.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.adaptive(letterRegion)))));
        }

        // Also try the entire left region
        results.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.createHighContrast(gray)))));
        results.add(normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(ImageUtils.adaptive(gray)))));

        return majorityLetter(results.toArray(new String[0]));
    }

    private Mat applyDigitMorphology(Mat gray) {
        Mat binary = ImageUtils.createHighContrast(gray);

        // Use closing to connect broken digits
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(2, 2));
        Mat morphed = new Mat();
        opencv_imgproc.morphologyEx(binary, morphed, opencv_imgproc.MORPH_CLOSE, kernel);

        return morphed;
    }

    private Mat enhanceForArabicText(Mat gray) {
        // Special enhancement for Arabic text which might be more delicate
        Mat enhanced = gray.clone();

        // Gentle contrast enhancement
        opencv_imgproc.equalizeHist(enhanced, enhanced);

        // Light Gaussian blur to reduce noise
        opencv_imgproc.GaussianBlur(enhanced, enhanced, new Size(1, 1), 0);

        // Adaptive threshold for better text extraction
        Mat binary = new Mat();
        opencv_imgproc.adaptiveThreshold(enhanced, binary, 255,
                opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                opencv_imgproc.THRESH_BINARY, 15, 5);

        return binary;
    }

    private String cleanDigits(String digits) {
        if (digits == null) return "";

        // Remove all non-digit characters
        String cleaned = digits.replaceAll("\\D+", "");

        // UAE plates typically have 1-5 digits
        if (cleaned.length() > 5) {
            cleaned = cleaned.substring(0, 5);
        }

        return cleaned;
    }

    private String cleanLetter(String letter) {
        if (letter == null || letter.isEmpty()) return "";

        // Ensure it's a single uppercase letter
        letter = letter.toUpperCase().trim();
        letter = letter.replaceAll("[^A-Z]", "");

        if (letter.length() > 1) {
            // Take the most frequent character if multiple
            Map<Character, Integer> freq = new HashMap<>();
            for (char c : letter.toCharArray()) {
                freq.put(c, freq.getOrDefault(c, 0) + 1);
            }
            letter = String.valueOf(freq.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .get()
                    .getKey());
        }

        return letter;
    }
}