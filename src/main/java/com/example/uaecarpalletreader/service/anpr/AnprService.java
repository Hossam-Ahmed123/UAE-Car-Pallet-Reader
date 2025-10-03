package com.example.uaecarpalletreader.service.anpr;

import com.example.uaecarpalletreader.config.AnprProperties;
import com.example.uaecarpalletreader.model.anpr.AnprServiceResult;
import com.example.uaecarpalletreader.model.anpr.PlateReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnprService {

    private static final Logger log = LoggerFactory.getLogger(AnprService.class);

    private final AnprProperties properties;
    private final OpenCvYoloDetector detector;
    private final TesseractOcrEngine ocrEngine;

    public AnprService(AnprProperties properties, OpenCvYoloDetector detector, TesseractOcrEngine ocrEngine) {
        this.properties = properties;
        this.detector = detector;
        this.ocrEngine = ocrEngine;
    }

    public AnprServiceResult detectAndRead(BufferedImage image) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("ANPR service is disabled via configuration");
        }
        long modelStart = System.nanoTime();
        List<PlateCandidate> detections = detector.detect(image);
        long modelElapsed = System.nanoTime() - modelStart;

        long ocrStart = System.nanoTime();
        List<PlateReading> readings = new ArrayList<>(detections.size());
        for (PlateCandidate candidate : detections) {
            String text = ocrEngine.read(candidate.roi());
            readings.add(new PlateReading(candidate.boundingBox(), text, candidate.confidence()));
        }
        long ocrElapsed = System.nanoTime() - ocrStart;

        long modelTimeMs = Duration.ofNanos(modelElapsed).toMillis();
        long ocrTimeMs = Duration.ofNanos(ocrElapsed).toMillis();
        log.debug("ANPR detection produced {} candidates in {} ms (OCR {} ms)", readings.size(), modelTimeMs, ocrTimeMs);
        return new AnprServiceResult(readings, modelTimeMs, ocrTimeMs);
    }
}
