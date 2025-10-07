package com.uae.anpr.service.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.parser.UaePlateParser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultAggregatorTest {

    private ResultAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ResultAggregator(new UaePlateParser());
    }

    @Test
    void prefersStructuredCandidateEvenWithLowerRawConfidence() {
        List<OcrResult> candidates = List.of(
                new OcrResult("P5740", 0.99),
                new OcrResult("DUBAIF97344", 0.90),
                new OcrResult("F97344", 0.92),
                new OcrResult("F97344", 0.91));

        Optional<ResultAggregator.AggregatedResult> best = aggregator.selectBest(candidates, 0.85);

        assertTrue(best.isPresent());
        assertEquals("F97344", best.get().text());
    }

    @Test
    void returnsEmptyWhenBelowThreshold() {
        List<OcrResult> candidates = List.of(
                new OcrResult("P12", 0.60),
                new OcrResult("X99", 0.61));

        Optional<ResultAggregator.AggregatedResult> best = aggregator.selectBest(candidates, 0.80);

        assertTrue(best.isEmpty());
    }

    @Test
    void boostsRepeatedConsensus() {
        List<OcrResult> candidates = List.of(
                new OcrResult("ABU12345", 0.82),
                new OcrResult("ABU12345", 0.83),
                new OcrResult("ABU12345", 0.81));

        Optional<ResultAggregator.AggregatedResult> best = aggregator.selectBest(candidates, 0.80);

        assertTrue(best.isPresent());
        assertTrue(best.get().confidence() > 0.85, "Consensus should lift the aggregated confidence");
    }
}
