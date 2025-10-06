package com.dubaipolice.anpr.util;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils {
    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    public static Mat resize(Mat m, double scale) {
        Mat out = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.resize(
                m, out,
                new Size((int)Math.round(m.cols()*scale), (int)Math.round(m.rows()*scale)),
                0, 0, org.bytedeco.opencv.global.opencv_imgproc.INTER_CUBIC
        );
        return out;
    }

    public static Mat ensureHeight(Mat mat, int minHeight, int maxHeight) {
        if (mat.empty()) {
            return mat.clone();
        }
        int h = mat.rows();
        if (minHeight > 0 && h < minHeight) {
            return resize(mat, Math.min(4.0, minHeight / (double) h));
        }
        if (maxHeight > 0 && h > maxHeight) {
            return resize(mat, Math.max(0.25, maxHeight / (double) h));
        }
        return mat.clone();
    }

    public static Mat adaptive(Mat gray) {
        Mat th = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.adaptiveThreshold(
                gray, th, 255,
                org.bytedeco.opencv.global.opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY, 31, 5
        );
        return th;
    }

    public static Mat readMat(byte[] bytes) {
        Mat buf = new Mat(1, bytes.length, opencv_core.CV_8U);
        buf.data().put(bytes);
        return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
    }

    public static Mat toGray(Mat src) {
        Mat g = new Mat();
        if (src.channels() == 1) {
            // Already grayscale
            return src.clone();
        }
        opencv_imgproc.cvtColor(src, g, opencv_imgproc.COLOR_BGR2GRAY);
        return g;
    }

    public static Mat enhanceForOCR(Mat gray) {
        // Ensure it's grayscale
        Mat grayMat = (gray.channels() == 3) ? toGray(gray) : gray.clone();

        // Apply CLAHE for better contrast
        try {
            Mat clahe = new Mat();
            opencv_imgproc.createCLAHE(2.0, new Size(8, 8)).apply(grayMat, clahe);
            grayMat = clahe;
        } catch (Exception e) {
            log.warn("CLAHE failed, using original: {}", e.getMessage());
        }

        // Denoise
        Mat denoised = new Mat();
        opencv_imgproc.medianBlur(grayMat, denoised, 3);

        // Threshold
        Mat th = new Mat();
        opencv_imgproc.threshold(denoised, th, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

        return th;
    }

    public static BufferedImage toBufferedImage(Mat mat) {
        BytePointer buf = new BytePointer();
        opencv_imgcodecs.imencode(".png", mat, buf);
        byte[] arr = buf.getStringBytes();
        try {
            return ImageIO.read(new ByteArrayInputStream(arr));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            buf.deallocate();
        }
    }

    // Enhanced preprocessing specifically for UAE plates
    public static Mat preprocessUaePlate(Mat src) {
        Mat processed = src.clone();

        // Ensure we have a color image for processing
        if (processed.channels() == 1) {
            opencv_imgproc.cvtColor(processed, processed, opencv_imgproc.COLOR_GRAY2BGR);
        }

        // Step 1: Resize if too small
        if (Math.max(processed.cols(), processed.rows()) < 400) {
            processed = resize(processed, 2.0);
        }

        // Step 2: Enhance contrast
        processed = enhanceContrast(processed);

        // Step 3: Remove noise
        processed = removeNoise(processed);

        // Step 4: Sharpen
        processed = sharpenImage(processed);

        return processed;
    }

    public static Mat enhanceContrast(Mat src) {
        try {
            // For grayscale images, use simple equalization
            if (src.channels() == 1) {
                Mat result = new Mat();
                opencv_imgproc.equalizeHist(src, result);
                return result;
            }

            // For color images, use LAB equalization
            Mat lab = new Mat();
            opencv_imgproc.cvtColor(src, lab, opencv_imgproc.COLOR_BGR2Lab);

            MatVector labChannels = new MatVector(3);
            opencv_core.split(lab, labChannels);

            Mat lightness = labChannels.get(0);
            opencv_imgproc.equalizeHist(lightness, lightness);

            opencv_core.merge(labChannels, lab);
            Mat result = new Mat();
            opencv_imgproc.cvtColor(lab, result, opencv_imgproc.COLOR_Lab2BGR);
            return result;

        } catch (Exception e) {
            log.warn("Contrast enhancement failed, using original: {}", e.getMessage());
            return src.clone();
        }
    }

    public static Mat sharpenImage(Mat src) {
        try {
            Mat sharpened = new Mat();
            float[] kernelData = {
                    0, -1, 0,
                    -1, 5, -1,
                    0, -1, 0
            };
            Mat kernel = new Mat(3, 3, opencv_core.CV_32F, new FloatPointer(kernelData));
            opencv_imgproc.filter2D(src, sharpened, -1, kernel);
            return sharpened;
        } catch (Exception e) {
            log.warn("Sharpening failed, using original: {}", e.getMessage());
            return src.clone();
        }
    }

    public static Mat removeNoise(Mat src) {
        Mat denoised = new Mat();
        opencv_imgproc.medianBlur(src, denoised, 3);
        return denoised;
    }

    // Simplified plate detection for small images
    public static Rect tryFindPlateROI(Mat srcBgr) {
        // For very small images (like 250x55), assume the entire image is the plate
        if (srcBgr.cols() <= 300 && srcBgr.rows() <= 100) {
            log.info("Small image detected, using full image as plate ROI");
            return new Rect(0, 0, srcBgr.cols(), srcBgr.rows());
        }

        // For larger images, try to find the plate
        try {
            Rect candidate = findPlateByContours(srcBgr);
            if (candidate != null && isValidPlate(candidate, srcBgr.size())) {
                return expandWithin(candidate, srcBgr.size(), 0.1);
            }

            candidate = findPlateByGradients(srcBgr);
            if (candidate != null && isValidPlate(candidate, srcBgr.size())) {
                return expandWithin(candidate, srcBgr.size(), 0.1);
            }
        } catch (Exception e) {
            log.warn("Plate detection failed: {}", e.getMessage());
        }

        return null;
    }

    private static boolean isValidPlate(Rect rect, Size imageSize) {
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) return false;

        double aspectRatio = rect.width() / (double) rect.height();
        double areaRatio = (rect.width() * rect.height()) / (imageSize.width() * imageSize.height());

        return aspectRatio >= 1.5 && aspectRatio <= 6.0 &&
                areaRatio >= 0.01 && areaRatio <= 0.4;
    }

    private static Rect findPlateByContours(Mat srcBgr) {
        Mat gray = toGray(srcBgr);
        Mat edges = new Mat();
        opencv_imgproc.Canny(gray, edges, 80, 200);

        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(5, 3));
        opencv_imgproc.morphologyEx(edges, edges, opencv_imgproc.MORPH_CLOSE, kernel);

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(edges, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        return selectBestContour(contours, srcBgr.size(), 0.02, 0.3);
    }

    private static Rect findPlateByGradients(Mat srcBgr) {
        Mat gray = toGray(srcBgr);
        Mat gradX = new Mat();
        opencv_imgproc.Sobel(gray, gradX, opencv_core.CV_16S, 1, 0, 3, 1, 0, opencv_core.BORDER_DEFAULT);
        Mat absGradX = new Mat();
        opencv_core.convertScaleAbs(gradX, absGradX);

        Mat blur = new Mat();
        opencv_imgproc.GaussianBlur(absGradX, blur, new Size(5, 5), 0);

        Mat binary = new Mat();
        opencv_imgproc.threshold(blur, binary, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(20, 5));
        opencv_imgproc.morphologyEx(binary, binary, opencv_imgproc.MORPH_CLOSE, kernel);

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(binary, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        return selectBestContour(contours, srcBgr.size(), 0.02, 0.3);
    }

    private static Rect selectBestContour(MatVector contours, Size bounds, double minAreaRatio, double maxAreaRatio) {
        if (contours == null || contours.size() == 0) {
            return null;
        }
        double imageArea = Math.max(1.0, bounds.width() * (double) bounds.height());
        double bestScore = 0;
        Rect best = null;

        for (long i = 0; i < contours.size(); i++) {
            Mat cnt = contours.get(i);
            Rect r = opencv_imgproc.boundingRect(cnt);
            if (r.width() <= 0 || r.height() <= 0) {
                continue;
            }

            double ar = r.width() / (double) r.height();
            if (ar < 1.5 || ar > 6.0) {
                continue;
            }

            double area = r.width() * (double) r.height();
            double areaRatio = area / imageArea;
            if (areaRatio < minAreaRatio || areaRatio > maxAreaRatio) {
                continue;
            }

            double contourArea = Math.max(opencv_imgproc.contourArea(cnt), 1.0);
            double fillRatio = contourArea / area;
            if (fillRatio < 0.2) {
                continue;
            }

            double aspectScore = 1.0 - Math.min(Math.abs(ar - 3.5) / 3.5, 1.0);
            double areaScore = Math.min(areaRatio / 0.1, 1.0);
            double fillScore = Math.min(fillRatio / 0.5, 1.0);

            double score = aspectScore * 0.4 + areaScore * 0.3 + fillScore * 0.3;

            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    private static Rect expandWithin(Rect rect, Size bounds, double paddingRatio) {
        if (rect == null) {
            return null;
        }
        int width = bounds.width();
        int height = bounds.height();
        int padX = (int) Math.round(rect.width() * paddingRatio);
        int padY = (int) Math.round(rect.height() * paddingRatio);
        int x = Math.max(rect.x() - padX, 0);
        int y = Math.max(rect.y() - padY, 0);
        int w = Math.min(rect.width() + padX * 2, width - x);
        int h = Math.min(rect.height() + padY * 2, height - y);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new Rect(x, y, w, h);
    }

    // Helper method to convert BufferedImage to byte array
    public static byte[] bufferedImageToByteArray(BufferedImage bi) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert image to byte array", e);
        }
    }

    // Create high contrast binary image
    public static Mat createHighContrast(Mat gray) {
        Mat highContrast = new Mat();
        opencv_imgproc.threshold(gray, highContrast, 0, 255,
                opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);
        return highContrast;
    }

    // Invert binary image
    public static Mat invertImage(Mat binary) {
        Mat inverted = new Mat();
        opencv_core.bitwise_not(binary, inverted);
        return inverted;
    }

    // Enhanced method for UAE plate specific processing
    public static Mat enhanceUaePlateRegion(Mat plate) {
        Mat enhanced = plate.clone();

        // Ensure grayscale for processing
        if (enhanced.channels() == 3) {
            enhanced = toGray(enhanced);
        }

        // Resize to optimal size for OCR
        if (enhanced.rows() < 100) {
            enhanced = resize(enhanced, 2.0);
        }

        // Apply CLAHE for local contrast enhancement
        try {
            Mat clahe = new Mat();
            opencv_imgproc.createCLAHE(3.0, new Size(8, 8)).apply(enhanced, clahe);
            enhanced = clahe;
        } catch (Exception e) {
            log.warn("CLAHE failed, using original: {}", e.getMessage());
        }

        // Denoise
        Mat denoised = new Mat();
        opencv_imgproc.medianBlur(enhanced, denoised, 3);

        return denoised;
    }

    public static Mat prepareDubaiPlate(Mat src) {
        if (src == null || src.empty()) {
            return new Mat();
        }

        Mat plate = src.clone();
        if (plate.channels() == 3) {
            opencv_imgproc.cvtColor(plate, plate, opencv_imgproc.COLOR_BGR2GRAY);
        }

        // UAE plates are rectangular – straighten aspect by resizing to canonical size
        plate = ensureHeight(plate, 220, 360);
        opencv_imgproc.GaussianBlur(plate, plate, new Size(3, 3), 0);
        opencv_imgproc.equalizeHist(plate, plate);
        plate = enhanceUaePlateRegion(plate);
        plate = ensureHeight(plate, 240, 320);
        return plate;
    }

    public static List<Mat> generateDubaiPlateLevels(Mat src, int levels) {
        List<Mat> variants = new ArrayList<>();
        if (src == null || src.empty() || levels <= 0) {
            return variants;
        }

        Mat base = prepareDubaiPlate(src);
        if (base.empty()) {
            return variants;
        }

        variants.add(base);
        if (levels == 1) {
            return variants;
        }

        double minScale = 0.85;
        double maxScale = 1.30;
        double minAlpha = 0.9;
        double maxAlpha = 1.4;

        for (int i = 1; i < levels; i++) {
            double t = i / (double) (levels - 1);
            double scale = minScale + (maxScale - minScale) * t;
            double alpha = minAlpha + (maxAlpha - minAlpha) * t;
            double beta = (t - 0.5) * 40.0;

            Mat scaled = ensureHeight(resize(base, scale), 240, 340);
            Mat contrasted = adjustContrast(scaled, alpha, beta);
            Mat sharpened = applyUnsharpMask(contrasted, 0.6 + 0.4 * t);
            variants.add(sharpened);
        }

        return variants;
    }

    public static Rect relativeRect(Mat mat, double x, double y, double width, double height) {
        if (mat == null || mat.empty()) {
            return new Rect();
        }
        int W = mat.cols();
        int H = mat.rows();

        int rx = clamp((int) Math.round(W * x), 0, W - 1);
        int ry = clamp((int) Math.round(H * y), 0, H - 1);
        int rW = clamp((int) Math.round(W * width), 1, W - rx);
        int rH = clamp((int) Math.round(H * height), 1, H - ry);
        return new Rect(rx, ry, rW, rH);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static List<Mat> generateDigitVariants(Mat digitsRegion) {
        Mat gray = toGray(digitsRegion);
        List<Mat> variants = new ArrayList<>();

        Mat upscaled = ensureHeight(gray, 160, 320);
        variants.add(enhanceForOCR(upscaled));

        Mat adaptive = adaptive(upscaled);
        variants.add(removeBorderNoise(adaptive));

        Mat morph = adaptive.clone();
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(3, 3));
        opencv_imgproc.morphologyEx(adaptive, morph, opencv_imgproc.MORPH_CLOSE, kernel, new Point(-1, -1), 1, opencv_core.BORDER_DEFAULT, new Scalar(0));
        variants.add(removeBorderNoise(morph));

        Mat highContrast = createHighContrast(upscaled);
        variants.add(removeBorderNoise(highContrast));

        // Slight erosion/dilation pair to balance thickness
        Mat opened = new Mat();
        opencv_imgproc.morphologyEx(highContrast, opened, opencv_imgproc.MORPH_OPEN, kernel);
        variants.add(removeBorderNoise(opened));

        return variants;
    }

    public static Mat prepareDubaiLetter(Mat letterRegion) {
        Mat gray = ensureHeight(toGray(letterRegion), 160, 260);
        Mat enhanced = enhanceForSmallText(gray);
        Mat binary = createHighContrast(enhanced);
        return removeBorderNoise(binary);
    }

    public static Mat prepareDubaiEmirate(Mat emirateRegion) {
        Mat gray = ensureHeight(toGray(emirateRegion), 180, 260);
        Mat clahe = new Mat();
        try {
            opencv_imgproc.createCLAHE(2.5, new Size(8, 8)).apply(gray, clahe);
        } catch (Exception e) {
            log.debug("CLAHE for emirate failed: {}", e.getMessage());
            clahe = gray;
        }
        Mat adaptive = adaptive(clahe);
        Mat denoised = removeBorderNoise(adaptive);
        return denoised;
    }

    public static List<Mat> generateDubaiLetterVariants(Mat plate) {
        List<Mat> variants = new ArrayList<>();
        if (plate == null || plate.empty()) {
            return variants;
        }

        double[][] rects = new double[][]{
                {0.70, 0.05, 0.26, 0.55},
                {0.68, 0.10, 0.28, 0.50},
                {0.72, 0.20, 0.22, 0.55}
        };

        for (double[] r : rects) {
            Rect rect = relativeRect(plate, r[0], r[1], r[2], r[3]);
            if (rect.width() <= 0 || rect.height() <= 0) {
                continue;
            }
            Mat region = new Mat(plate, rect).clone();
            Mat prepared = prepareDubaiLetter(region);
            variants.add(prepared);
            variants.add(invertImage(prepared));
        }

        Rect topBand = relativeRect(plate, 0.58, 0.00, 0.40, 0.40);
        if (topBand.width() > 0 && topBand.height() > 0) {
            Mat topRegion = new Mat(plate, topBand).clone();
            Mat preparedTop = prepareDubaiLetter(topRegion);
            variants.add(preparedTop);
            variants.add(invertImage(preparedTop));
        }

        return variants;
    }

    private static Mat applyUnsharpMask(Mat src, double amount) {
        Mat blurred = new Mat();
        opencv_imgproc.GaussianBlur(src, blurred, new Size(0, 0), 1.0);
        Mat sharpened = new Mat();
        opencv_core.addWeighted(src, 1.0 + amount, blurred, -amount, 0, sharpened);
        return sharpened;
    }

    public static Mat adjustContrast(Mat src, double alpha, double beta) {
        Mat adjusted = new Mat();
        src.convertTo(adjusted, -1, alpha, beta);
        return adjusted;
    }










    // Add these methods to ImageUtils.java:

    public static Mat enhanceForSmallText(Mat gray) {
        // Special enhancement for small text in license plates
        Mat enhanced = gray.clone();

        // Upscale if too small
        if (enhanced.rows() < 100) {
            enhanced = resize(enhanced, 2.0);
        }

        // Apply strong CLAHE for small text
        try {
            Mat clahe = new Mat();
            opencv_imgproc.createCLAHE(4.0, new Size(4, 4)).apply(enhanced, clahe);
            enhanced = clahe;
        } catch (Exception e) {
            log.warn("CLAHE failed for small text enhancement: {}", e.getMessage());
        }

        // Light sharpening
        Mat sharpened = new Mat();
        float[] kernelData = {
                0, -0.5f, 0,
                -0.5f, 3, -0.5f,
                0, -0.5f, 0
        };
        Mat kernel = new Mat(3, 3, opencv_core.CV_32F, new FloatPointer(kernelData));
        opencv_imgproc.filter2D(enhanced, sharpened, -1, kernel);

        return sharpened;
    }

    public static Mat removeBorderNoise(Mat binary) {
        // Remove border noise that might interfere with OCR
        Mat cleaned = binary.clone();

        // Define a small border to clear
        int border = Math.max(2, cleaned.rows() / 50);
        opencv_imgproc.rectangle(cleaned,
                new Point(0, 0),
                new Point(cleaned.cols(), border),
                new Scalar(0.0));

        opencv_imgproc.rectangle(cleaned,
                new Point(0, cleaned.rows() - border),
                new Point(cleaned.cols(), cleaned.rows()),
                new Scalar(0.0));

        opencv_imgproc.rectangle(cleaned,
                new Point(0, 0),
                new Point(border, cleaned.rows()),
                new Scalar(0.0));

        opencv_imgproc.rectangle(cleaned,
                new Point(cleaned.cols() - border, 0),
                new Point(cleaned.cols(), cleaned.rows()),
                new Scalar(0.0));

        return cleaned;
    }
}