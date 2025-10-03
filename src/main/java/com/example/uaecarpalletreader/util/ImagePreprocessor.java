package com.example.uaecarpalletreader.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;

/**
 * Image preprocessing utilities used before sending a plate region to
 * Tesseract. The steps implemented here are deliberately lightweight so they
 * work without any native dependencies: grayscale conversion, contrast
 * stretching, edge sharpening and global thresholding.
 */
public final class ImagePreprocessor {

    private ImagePreprocessor() {
    }

    public static BufferedImage preprocess(BufferedImage input) {
        if (input == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }

        BufferedImage grayscale = toGrayscale(input);
        BufferedImage enhanced = stretchContrast(grayscale);
        BufferedImage sharpened = sharpen(enhanced);
        return applyBinaryThreshold(sharpened);
    }

    private static BufferedImage toGrayscale(BufferedImage input) {
        BufferedImage grayscale = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayscale.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(input, 0, 0, null);
        g.dispose();
        return grayscale;
    }

    private static BufferedImage stretchContrast(BufferedImage input) {
        RescaleOp rescaleOp = new RescaleOp(1.6f, -20f, null);
        rescaleOp.filter(input, input);
        return input;
    }

    private static BufferedImage sharpen(BufferedImage input) {
        float[] sharpenKernel = new float[]{
                0f, -1f, 0f,
                -1f, 5f, -1f,
                0f, -1f, 0f
        };
        ConvolveOp convolve = new ConvolveOp(new Kernel(3, 3, sharpenKernel), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage destination = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        convolve.filter(input, destination);
        return destination;
    }

    private static BufferedImage applyBinaryThreshold(BufferedImage input) {
        int width = input.getWidth();
        int height = input.getHeight();
        BufferedImage thresholded = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        long total = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = input.getRaster().getSample(x, y, 0);
                total += gray;
            }
        }
        int threshold = (int) (total / Math.max(1, width * height));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = input.getRaster().getSample(x, y, 0);
                int value = gray >= threshold ? 255 : 0;
                thresholded.getRaster().setSample(x, y, 0, value);
            }
        }
        return thresholded;
    }
}

