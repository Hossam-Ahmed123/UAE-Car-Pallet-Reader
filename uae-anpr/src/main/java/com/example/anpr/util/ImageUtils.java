package com.example.anpr.util;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.javacpp.BytePointer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;

public class ImageUtils {
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
        opencv_imgproc.cvtColor(src, g, opencv_imgproc.COLOR_BGR2GRAY);
        return g;
    }

    public static Mat enhanceForOCR(Mat gray) {
        Mat denoise = new Mat();
        opencv_imgproc.bilateralFilter(gray, denoise, 9, 75, 75);
        Mat th = new Mat();
        opencv_imgproc.threshold(denoise, th, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);
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

    /** Try to find a license-plate-like rectangle using contours. */
    public static Rect tryFindPlateROI(Mat srcBgr) {
        Rect candidate = findPlateByContours(srcBgr);
        if (candidate != null) {
            return expandWithin(candidate, srcBgr.size(), 0.08);
        }
        candidate = findPlateByGradients(srcBgr);
        if (candidate != null) {
            return expandWithin(candidate, srcBgr.size(), 0.10);
        }
        return null;
    }

    private static Rect findPlateByContours(Mat srcBgr) {
        Mat gray = toGray(srcBgr);
        Mat edges = new Mat();
        opencv_imgproc.Canny(gray, edges, 50, 150);

        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(5,3));
        opencv_imgproc.morphologyEx(edges, edges, opencv_imgproc.MORPH_CLOSE, kernel);

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(edges, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        return selectBestContour(contours, srcBgr.size(), 0.0005, 0.25);
    }

    private static Rect findPlateByGradients(Mat srcBgr) {
        Mat gray = toGray(srcBgr);
        Mat gradX = new Mat();
        opencv_imgproc.Sobel(gray, gradX, opencv_core.CV_16S, 1, 0, 3, 1, 0, opencv_core.BORDER_DEFAULT);
        Mat absGradX = new Mat();
        opencv_core.convertScaleAbs(gradX, absGradX);
        Mat blur = new Mat();
        opencv_imgproc.GaussianBlur(absGradX, blur, new Size(5,5), 0);
        Mat binary = new Mat();
        opencv_imgproc.threshold(blur, binary, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);

        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(17,5));
        opencv_imgproc.morphologyEx(binary, binary, opencv_imgproc.MORPH_CLOSE, kernel);
        opencv_imgproc.dilate(binary, binary, kernel);

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(binary, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        Rect candidate = selectBestContour(contours, srcBgr.size(), 0.0003, 0.3);
        if (candidate != null) {
            return candidate;
        }

        MatVector refinedContours = new MatVector();
        opencv_imgproc.morphologyEx(binary, binary, opencv_imgproc.MORPH_OPEN, kernel);
        opencv_imgproc.findContours(binary, refinedContours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);
        return selectBestContour(refinedContours, srcBgr.size(), 0.0003, 0.3);
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
            if (ar < 2.0 || ar > 8.5) {
                continue;
            }
            double area = r.width() * (double) r.height();
            double areaRatio = area / imageArea;
            if (areaRatio < minAreaRatio || areaRatio > maxAreaRatio) {
                continue;
            }
            double contourArea = Math.max(opencv_imgproc.contourArea(cnt), 1.0);
            double fillRatio = contourArea / area;
            if (fillRatio < 0.35) {
                continue;
            }
            double aspectScore = 1.0 - Math.min(Math.abs(ar - 4.5) / 4.5, 0.9);
            double score = areaRatio * (0.6 + 0.4 * fillRatio) * aspectScore;
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
}
