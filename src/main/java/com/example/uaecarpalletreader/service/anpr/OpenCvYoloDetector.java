package com.example.uaecarpalletreader.service.anpr;

import com.example.uaecarpalletreader.config.AnprProperties;
import com.example.uaecarpalletreader.model.BoundingBox;
import nu.pattern.OpenCV;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class OpenCvYoloDetector {

    private static final Logger log = LoggerFactory.getLogger(OpenCvYoloDetector.class);

    static {
        OpenCV.loadLocally();
        log.info("Loaded OpenCV native libraries");
    }

    private final AnprProperties properties;
    private volatile Net network;
    private final Object networkLock = new Object();

    public OpenCvYoloDetector(AnprProperties properties) {
        this.properties = properties;
    }

    public List<PlateCandidate> detect(BufferedImage image) {
        Objects.requireNonNull(image, "BufferedImage must not be null");
        Net net = ensureNetwork();
        Mat source = bufferedImageToMat(image);
        Size inputSize = new Size(properties.getInputSize(), properties.getInputSize());
        Mat blob = Dnn.blobFromImage(source, 1.0 / 255.0, inputSize, new Scalar(0, 0, 0), true, false);
        net.setInput(blob);
        Mat rawResult = net.forward();
        Mat reshaped = rawResult.reshape(1, (int) rawResult.size(2));
        float[] data = new float[(int) (reshaped.total() * reshaped.channels())];
        reshaped.get(0, 0, data);

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        float xFactor = imageWidth / (float) properties.getInputSize();
        float yFactor = imageHeight / (float) properties.getInputSize();
        int channels = reshaped.cols();

        List<Detection> detections = new ArrayList<>();
        for (int i = 0; i < reshaped.rows(); i++) {
            int offset = i * channels;
            float cx = data[offset];
            float cy = data[offset + 1];
            float w = data[offset + 2];
            float h = data[offset + 3];
            float objectness = data[offset + 4];
            float confidence = objectness;
            int classCount = channels - 5;
            float maxClassScore = 0f;
            for (int c = 0; c < classCount; c++) {
                float classScore = data[offset + 5 + c];
                if (classScore > maxClassScore) {
                    maxClassScore = classScore;
                }
            }
            if (classCount > 0) {
                confidence *= maxClassScore;
            }
            if (confidence < properties.getConfThreshold()) {
                continue;
            }

            int left = Math.round((cx - w / 2f) * xFactor);
            int top = Math.round((cy - h / 2f) * yFactor);
            int width = Math.round(w * xFactor);
            int height = Math.round(h * yFactor);

            left = clamp(left, 0, imageWidth - 1);
            top = clamp(top, 0, imageHeight - 1);
            width = clamp(width, 1, imageWidth - left);
            height = clamp(height, 1, imageHeight - top);

            Rect rect = new Rect(new Point(left, top), new Point(left + width, top + height));
            detections.add(new Detection(rect, confidence));
        }

        List<Detection> filtered = applyNms(detections, properties.getNmsThreshold());
        List<PlateCandidate> candidates = new ArrayList<>(filtered.size());
        for (Detection detection : filtered) {
            Rect rect = detection.box();
            Mat roi = new Mat(source, rect).clone();
            try {
                BufferedImage roiImage = matToBufferedImage(roi);
                BoundingBox boundingBox = new BoundingBox(rect.x, rect.y, rect.width, rect.height);
                candidates.add(new PlateCandidate(boundingBox, roiImage, detection.confidence()));
            } finally {
                roi.release();
            }
        }

        blob.release();
        reshaped.release();
        rawResult.release();
        source.release();
        return candidates;
    }

    private Net ensureNetwork() {
        Net current = network;
        if (current != null) {
            return current;
        }
        synchronized (networkLock) {
            if (network == null) {
                String modelPath = properties.getModelPath();
                if (modelPath == null || modelPath.isBlank()) {
                    throw new IllegalStateException("Model path must be configured");
                }
                Path path = Path.of(modelPath);
                if (!Files.exists(path)) {
                    log.warn("YOLO model file {} not found. Detection requests will fail until the model is available.", modelPath);
                } else {
                    log.info("Loading YOLO model from {}", path.toAbsolutePath());
                }
                network = Dnn.readNetFromONNX(modelPath);
            }
            return network;
        }
    }

    private Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        MatOfByte buffer = new MatOfByte();
        try {
            if (!ImageIO.getImageWritersByFormatName("png").hasNext()) {
                throw new IllegalStateException("PNG ImageWriter not available");
            }
            boolean encoded = org.opencv.imgcodecs.Imgcodecs.imencode(".png", mat, buffer);
            if (!encoded) {
                throw new IllegalStateException("Failed to encode ROI to PNG");
            }
            try (ByteArrayInputStream input = new ByteArrayInputStream(buffer.toArray())) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    throw new IllegalStateException("Unable to decode ROI image");
                }
                return image;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to convert ROI to BufferedImage", ex);
        } finally {
            buffer.release();
        }
    }

    private List<Detection> applyNms(List<Detection> detections, double threshold) {
        if (detections.isEmpty()) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>(detections.size());
        for (int i = 0; i < detections.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer idx) -> detections.get(idx).confidence()).reversed());
        List<Detection> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        for (int idx : order) {
            if (suppressed[idx]) {
                continue;
            }
            Detection current = detections.get(idx);
            kept.add(current);
            for (int j = 0; j < detections.size(); j++) {
                if (suppressed[j] || j == idx) {
                    continue;
                }
                double iou = intersectionOverUnion(current.box(), detections.get(j).box());
                if (iou > threshold) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    private double intersectionOverUnion(Rect a, Rect b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        int intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        int areaA = a.width * a.height;
        int areaB = b.width * b.height;
        int union = areaA + areaB - intersectionArea;
        if (union <= 0) {
            return 0d;
        }
        return intersectionArea / (double) union;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private record Detection(Rect box, double confidence) {
    }
}
