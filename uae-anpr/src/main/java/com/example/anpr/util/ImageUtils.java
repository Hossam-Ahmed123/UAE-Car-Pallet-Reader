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
        Mat gray = toGray(srcBgr);
        Mat edges = new Mat();
        opencv_imgproc.Canny(gray, edges, 50, 150);

        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(5,3));
        opencv_imgproc.morphologyEx(edges, edges, opencv_imgproc.MORPH_CLOSE, kernel);

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(edges, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        double bestScore = 0;
        Rect best = null;
        for (long i=0; i<contours.size(); i++) {
            Mat cnt = contours.get(i);
            Rect r = opencv_imgproc.boundingRect(cnt);
            double ar = r.width() / (double) r.height();
            double area = r.width() * (double) r.height();
            if (ar > 2.5 && ar < 6.5 && area > 2000) {
                double score = area;
                if (score > bestScore) { bestScore = score; best = r; }
            }
        }
        return best;
    }
}
