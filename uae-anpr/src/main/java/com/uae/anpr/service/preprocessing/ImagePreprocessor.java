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

    /**
     * Generates multiple enhanced variations for a plate candidate prior to OCR. The hybrid
     * strategy fuses morphological filtering, adaptive thresholding and spatial cropping to
     * emphasise both the alphanumeric body and the suffix character common on UAE plates.
     */
    public List<Mat> generateOcrVariants(Mat candidate) {
        List<Mat> baseVariants = new ArrayList<>();
        Mat normalized = upscaleToWidth(candidate, 480);
        baseVariants.add(normalized.clone());

        Mat gray = new Mat();
        opencv_imgproc.cvtColor(normalized, gray, opencv_imgproc.COLOR_BGR2GRAY);

        Mat clahe = new Mat();
        opencv_imgproc.createCLAHE(2.5, new Size(8, 8)).apply(gray, clahe);

        Mat blurred = new Mat();
        opencv_imgproc.GaussianBlur(clahe, blurred, new Size(3, 3), 0);

        Mat sharpened = new Mat();
        opencv_core.addWeighted(clahe, 1.5, blurred, -0.5, 0, sharpened);
        baseVariants.add(toColor(sharpened));

        Mat adaptive = new Mat();
        opencv_imgproc.adaptiveThreshold(sharpened, adaptive, 255,
                opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                opencv_imgproc.THRESH_BINARY, 35, 10);
        baseVariants.add(toColor(adaptive));

        Mat inverted = new Mat();
        opencv_core.bitwise_not(adaptive, inverted);
        baseVariants.add(toColor(inverted));

        Mat morphKernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(3, 3));
        Mat closed = new Mat();
        opencv_imgproc.morphologyEx(adaptive, closed, opencv_imgproc.MORPH_CLOSE, morphKernel);
        baseVariants.add(toColor(closed));

        // Crop the dominant numeric band occupying the lower section of UAE plates
        int numericTop = Math.max(0, (int) (normalized.rows() * 0.35));
        int numericHeight = normalized.rows() - numericTop;
        if (numericHeight > normalized.rows() / 3) {
            org.bytedeco.opencv.opencv_core.Rect digitsRect = new org.bytedeco.opencv.opencv_core.Rect(
                    0, numericTop, normalized.cols(), numericHeight);
            Mat digitsRegion = new Mat(normalized, digitsRect).clone();
            baseVariants.addAll(generateFocusedVariants(digitsRegion));
        }

        // Crop the right-most band that usually contains the classification letter(s)
        int letterWidth = (int) Math.round(normalized.cols() * 0.28);
        if (letterWidth > 30 && letterWidth < normalized.cols()) {
            org.bytedeco.opencv.opencv_core.Rect letterRect = new org.bytedeco.opencv.opencv_core.Rect(
                    normalized.cols() - letterWidth, 0, letterWidth, normalized.rows());
            Mat letterRegion = new Mat(normalized, letterRect).clone();
            baseVariants.addAll(generateFocusedVariants(letterRegion));
        }

        return expandWithLayoutPermutations(baseVariants);
    }

    private List<Mat> generateFocusedVariants(Mat region) {
        List<Mat> variants = new ArrayList<>();
        Mat resized = upscaleToWidth(region, 320);
        variants.add(resized.clone());

        Mat gray = new Mat();
        opencv_imgproc.cvtColor(resized, gray, opencv_imgproc.COLOR_BGR2GRAY);

        Mat clahe = new Mat();
        opencv_imgproc.createCLAHE(2.3, new Size(8, 8)).apply(gray, clahe);

        Mat thresh = new Mat();
        opencv_imgproc.adaptiveThreshold(clahe, thresh, 255,
                opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
                opencv_imgproc.THRESH_BINARY, 31, 8);

        Mat dilated = new Mat();
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(2, 2));
        opencv_imgproc.dilate(thresh, dilated, kernel);

        variants.add(toColor(clahe));
        variants.add(toColor(thresh));
        variants.add(toColor(dilated));
        return variants;
    }

    private List<Mat> expandWithLayoutPermutations(List<Mat> baseVariants) {
        List<Mat> expanded = new ArrayList<>();
        for (Mat variant : baseVariants) {
            expanded.add(variant);
            expanded.addAll(generateLayoutPermutations(variant));
        }
        return expanded;
    }

    private List<Mat> generateLayoutPermutations(Mat source) {
        List<Mat> permutations = new ArrayList<>();
        if (source == null || source.empty()) {
            return permutations;
        }
        permutations.add(rotateClone(source, opencv_core.ROTATE_90_CLOCKWISE));
        permutations.add(rotateClone(source, opencv_core.ROTATE_90_COUNTERCLOCKWISE));
        permutations.add(rotateClone(source, opencv_core.ROTATE_180));
        permutations.add(flipClone(source, 1));
        permutations.add(flipClone(source, 0));
        return permutations;
    }

    private Mat rotateClone(Mat source, int rotationCode) {
        Mat rotated = new Mat();
        opencv_core.rotate(source, rotated, rotationCode);
        return rotated;
    }

    private Mat flipClone(Mat source, int flipCode) {
        Mat flipped = new Mat();
        opencv_core.flip(source, flipped, flipCode);
        return flipped;
    }

    private Mat upscaleToWidth(Mat source, int minWidth) {
        if (source.cols() <= 0) {
            return source.clone();
        }
        if (source.cols() >= minWidth) {
            return source.clone();
        }
        Mat resized = new Mat();
        double scale = minWidth / (double) source.cols();
        opencv_imgproc.resize(source, resized, new Size(), scale, scale, opencv_imgproc.INTER_CUBIC);
        return resized;
    }

    private Mat toColor(Mat input) {
        if (input.channels() == 1) {
            Mat color = new Mat();
            opencv_imgproc.cvtColor(input, color, opencv_imgproc.COLOR_GRAY2BGR);
            return color;
        }
        return input.clone();
    }
}
