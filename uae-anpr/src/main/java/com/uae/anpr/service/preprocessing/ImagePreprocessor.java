package com.uae.anpr.service.preprocessing;

import java.util.ArrayList;
import java.util.List;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implements deterministic enhancement steps that mirror the mathematical foundations of
 * Automatic Number Plate Recognition systems. Each transformation isolates plate regions by
 * combining spatial filtering, morphological gradients and adaptive binarisation.
 */
@Component
public class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);

    /**
     * Decodes an arbitrary payload, then performs scale normalisation to enforce a minimum Nyquist
     * sampling rate for the typographical features present in UAE plates.
     */
    public Mat loadAndNormalize(byte[] imageBytes) {
        Mat raw = opencv_imgcodecs.imdecode(new Mat(imageBytes), opencv_imgcodecs.IMREAD_COLOR);
        if (raw == null || raw.empty()) {
            throw new IllegalArgumentException("Unable to decode image payload");
        }
        Mat resized = new Mat();
        int width = raw.cols();
        int height = raw.rows();
        if (width < 640) {
            double scale = 640.0 / width;
            opencv_imgproc.resize(raw, resized, new Size(), scale, scale, opencv_imgproc.INTER_CUBIC);
            log.debug("Upscaled image from {}x{} to {}x{}", width, height, resized.cols(), resized.rows());
        } else {
            resized = raw.clone();
        }
        return resized;
    }

    /**
     * Executes a grayscale conversion, contrast limited adaptive histogram equalisation (CLAHE)
     * and bilateral filtering before highlighting plate ridges through a top-hat operator.
     */
    public Mat enhanceContrast(Mat input) {
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(input, gray, opencv_imgproc.COLOR_BGR2GRAY);

        Mat claheResult = new Mat();
        opencv_imgproc.createCLAHE(2.0, new Size(8, 8)).apply(gray, claheResult);

        Mat bilateral = new Mat();
        opencv_imgproc.bilateralFilter(claheResult, bilateral, 5, 75, 75);

        Mat morphKernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(3, 3));
        Mat morph = new Mat();
        opencv_imgproc.morphologyEx(bilateral, morph, opencv_imgproc.MORPH_TOPHAT, morphKernel);

        Mat normalized = new Mat();
        opencv_core.normalize(morph, normalized, 0, 255, opencv_core.NORM_MINMAX, opencv_core.CV_8U, new Mat());
        return normalized;
    }

    /**
     * Converts the enhanced raster into a binary image using adaptive thresholding and morphological
     * closure. This approximates the probability mass of foreground characters required for a
     * connected-components search.
     */
    public Mat binarize(Mat enhanced) {
        Mat adaptive = new Mat();
        opencv_imgproc.adaptiveThreshold(enhanced, adaptive, 255,
                opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
                opencv_imgproc.THRESH_BINARY, 35, 15);

        Mat morphKernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(5, 5));
        Mat closed = new Mat();
        opencv_imgproc.morphologyEx(adaptive, closed, opencv_imgproc.MORPH_CLOSE, morphKernel);
        return closed;
    }

    /**
     * Performs contour extraction followed by geometric filtering to retain candidate regions that
     * satisfy the planar aspect-ratio and area constraints of UAE plates.
     */
    public List<Mat> extractCandidates(Mat binaryImage, Mat originalColor) {
        Mat contoursMat = binaryImage.clone();
        List<Mat> candidates = new ArrayList<>();

        org.bytedeco.opencv.opencv_core.MatVector contours = new org.bytedeco.opencv.opencv_core.MatVector();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(contoursMat, contours, hierarchy,
                opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        for (long i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            org.bytedeco.opencv.opencv_core.Rect rect = opencv_imgproc.boundingRect(contour);
            double aspect = rect.width() / (double) rect.height();
            double area = rect.width() * (double) rect.height();
            if (aspect >= 2.0 && aspect <= 6.5 && area > 4000) {
                Mat candidate = new Mat(originalColor, rect).clone();
                candidates.add(candidate);
            }
        }
        log.debug("Extracted {} plate candidates", candidates.size());
        return candidates;
    }
}
