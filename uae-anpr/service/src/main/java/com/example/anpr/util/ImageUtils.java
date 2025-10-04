package com.example.anpr.util;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatOfByte;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public final class ImageUtils {

    private ImageUtils() {
    }

    public static Mat readImage(byte[] data) {
        MatOfByte mob = new MatOfByte(data);
        Mat image = opencv_imgcodecs.imdecode(mob, opencv_imgcodecs.IMREAD_COLOR);
        mob.releaseReference();
        return image;
    }

    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.data().get(buffer);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, target, 0, buffer.length);
        return image;
    }

    public static Mat bufferedImageToMat(BufferedImage image) {
        int type = image.getType() == BufferedImage.TYPE_BYTE_GRAY ? opencv_imgcodecs.IMREAD_GRAYSCALE : opencv_imgcodecs.IMREAD_COLOR;
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(), image.getRaster().getNumBands() == 1 ? opencv_core.CV_8UC1 : opencv_core.CV_8UC3);
        mat.data().put(pixels);
        if (type == opencv_imgcodecs.IMREAD_COLOR) {
            Mat converted = new Mat();
            opencv_imgproc.cvtColor(mat, converted, opencv_imgproc.COLOR_BGR2RGB);
            mat.close();
            return converted;
        }
        return mat;
    }

    public static Rect clipRect(Rect rect, Mat bounds) {
        int x = Math.max(rect.x(), 0);
        int y = Math.max(rect.y(), 0);
        int w = Math.min(rect.width(), bounds.cols() - x);
        int h = Math.min(rect.height(), bounds.rows() - y);
        return new Rect(x, y, Math.max(w, 0), Math.max(h, 0));
    }

    public static Mat resizeWithLetterbox(Mat src, int size, Scalar color, double[] scale, int[] pad) {
        int width = src.cols();
        int height = src.rows();
        double r = Math.min(size / (double) width, size / (double) height);
        int newWidth = (int) Math.round(width * r);
        int newHeight = (int) Math.round(height * r);

        Mat resized = new Mat();
        opencv_imgproc.resize(src, resized, new org.bytedeco.opencv.opencv_core.Size(newWidth, newHeight));
        int dw = size - newWidth;
        int dh = size - newHeight;
        int top = (int) Math.floor(dh / 2.0);
        int bottom = dh - top;
        int left = (int) Math.floor(dw / 2.0);
        int right = dw - left;
        Mat bordered = new Mat();
        opencv_core.copyMakeBorder(resized, bordered, top, bottom, left, right, opencv_core.BORDER_CONSTANT, color);
        scale[0] = r;
        pad[0] = left;
        pad[1] = top;
        resized.close();
        return bordered;
    }
}
