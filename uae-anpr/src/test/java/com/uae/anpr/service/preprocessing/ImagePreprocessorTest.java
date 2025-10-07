package com.uae.anpr.service.preprocessing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.Test;

class ImagePreprocessorTest {

    private final ImagePreprocessor preprocessor = new ImagePreprocessor();

    @Test
    void generateOcrVariantsProducesDiverseLayouts() {
        Mat candidate = new Mat(240, 120, opencv_core.CV_8UC3, new Scalar(200, 200, 200, 0));

        List<Mat> variants = preprocessor.generateOcrVariants(candidate);

        assertTrue(variants.size() >= 10, "Expected at least ten OCR layout variants");
        boolean hasLandscape = variants.stream().anyMatch(mat -> mat.cols() > mat.rows());
        boolean hasPortrait = variants.stream().anyMatch(mat -> mat.rows() > mat.cols());

        assertTrue(hasLandscape, "Variants should include landscape orientation");
        assertTrue(hasPortrait, "Variants should include portrait orientation");
    }
}
