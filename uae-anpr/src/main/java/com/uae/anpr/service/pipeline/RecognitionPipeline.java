package com.uae.anpr.service.pipeline;

import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.service.ResourceScanner;
import com.uae.anpr.service.ocr.TesseractOcrEngine;
import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.preprocessing.ImagePreprocessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecognitionPipeline {

    private static final Logger log = LoggerFactory.getLogger(RecognitionPipeline.class);

    private final ImagePreprocessor preprocessor;
    private final TesseractOcrEngine ocrEngine;
    private final AnprProperties properties;

    public RecognitionPipeline(ImagePreprocessor preprocessor,
                               TesseractOcrEngine ocrEngine,
                               AnprProperties properties,
                               ResourceScanner scanner) {
        this.preprocessor = preprocessor;
        this.ocrEngine = ocrEngine;
        this.properties = properties;
        log.info("ANPR resources:\n{}", scanner.describeResources());
    }

    public Optional<OcrResult> recognize(byte[] imageBytes) {
        Mat normalized = preprocessor.loadAndNormalize(imageBytes);
        Mat enhanced = preprocessor.enhanceContrast(normalized);
        Mat binary = preprocessor.binarize(enhanced);
        List<Mat> candidates = preprocessor.extractCandidates(binary, normalized);
        List<OcrResult> recognized = new ArrayList<>();

        for (Mat candidate : candidates) {
            preprocessor.generateOcrVariants(candidate).stream()
                    .map(ocrEngine::recognize)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(recognized::add);
        }

        if (recognized.isEmpty()) {
            preprocessor.generateOcrVariants(normalized).stream()
                    .map(ocrEngine::recognize)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(recognized::add);
        }

        log.debug("Recognized {} candidate hypotheses from {} plate candidates", recognized.size(), candidates.size());

        return recognized.stream()
                .filter(result -> result.confidence() >= properties.ocr().confidenceThreshold())
                .max(Comparator.comparingDouble(OcrResult::confidence));
    }
}
