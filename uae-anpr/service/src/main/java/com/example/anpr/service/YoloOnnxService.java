package com.example.anpr.service;

import com.example.anpr.config.AnprProperties;
import com.example.anpr.util.ImageUtils;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.microsoft.onnxruntime.OrtEnvironment;
import com.microsoft.onnxruntime.OrtException;
import com.microsoft.onnxruntime.OrtSession;
import com.microsoft.onnxruntime.OnnxTensor;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class YoloOnnxService {

    private static final Logger log = LoggerFactory.getLogger(YoloOnnxService.class);

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final AnprProperties properties;

    public YoloOnnxService(OrtEnvironment environment, OrtSession session, AnprProperties properties) {
        this.environment = environment;
        this.session = session;
        this.properties = properties;
    }

    public List<Detection> detect(Mat original) throws OrtException {
        long start = System.nanoTime();
        double[] scale = new double[1];
        int[] pad = new int[2];
        Mat letterbox = ImageUtils.resizeWithLetterbox(original, properties.getImgsz(), new Scalar(114, 114, 114, 0), scale, pad);
        Mat rgb = new Mat();
        opencv_imgproc.cvtColor(letterbox, rgb, opencv_imgproc.COLOR_BGR2RGB);
        Mat floatImage = new Mat();
        rgb.convertTo(floatImage, opencv_core.CV_32FC3, 1.0 / 255.0);

        FloatBuffer buffer = FloatBuffer.allocate(properties.getImgsz() * properties.getImgsz() * 3);
        int rows = floatImage.rows();
        int cols = floatImage.cols();
        int channels = floatImage.channels();
        try (FloatIndexer indexer = floatImage.createIndexer()) {
            for (int c = 0; c < channels; c++) {
                for (int y = 0; y < rows; y++) {
                    for (int x = 0; x < cols; x++) {
                        buffer.put(c * rows * cols + y * cols + x, indexer.get(y, x, c));
                    }
                }
            }
        }
        floatImage.close();
        rgb.close();
        letterbox.close();

        buffer.rewind();
        long[] shape = new long[]{1, 3, properties.getImgsz(), properties.getImgsz()};
        OnnxTensor inputTensor = OnnxTensor.createTensor(environment, buffer, shape);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(session.getInputNames().iterator().next(), inputTensor);
        long preprocessEnd = System.nanoTime();

        OrtSession.Result output = session.run(inputs);
        long inferenceEnd = System.nanoTime();
        inputTensor.close();

        OnnxTensor tensor = (OnnxTensor) output.get(0);
        long[] outputShape = tensor.getInfo().getShape();
        FloatBuffer raw = tensor.getFloatBuffer();
        // Ultralytics YOLO ONNX export produces a tensor of shape [1, 84, 8400] (channels first)
        // or [1, 8400, 84] (channels last) depending on opset. Each row contains
        // [cx, cy, width, height, objectness, class_scores...]. We only trained one
        // class, so the class_scores array has a single element. Confidence is
        // computed as objectness * class_score. The coordinates are relative to the
        // letterboxed 640x640 image.
        int numBoxes;
        int numFeatures;
        boolean channelFirst;
        if (outputShape.length == 3) {
            if (outputShape[1] > outputShape[2]) {
                numFeatures = (int) outputShape[1];
                numBoxes = (int) outputShape[2];
                channelFirst = true;
            } else {
                numFeatures = (int) outputShape[2];
                numBoxes = (int) outputShape[1];
                channelFirst = false;
            }
        } else {
            throw new IllegalStateException("Unexpected YOLO output shape");
        }

        List<Detection> detections = new ArrayList<>();
        for (int i = 0; i < numBoxes; i++) {
            float cx = getValue(raw, i, 0, numBoxes, numFeatures, channelFirst);
            float cy = getValue(raw, i, 1, numBoxes, numFeatures, channelFirst);
            float w = getValue(raw, i, 2, numBoxes, numFeatures, channelFirst);
            float h = getValue(raw, i, 3, numBoxes, numFeatures, channelFirst);
            float objectness = getValue(raw, i, 4, numBoxes, numFeatures, channelFirst);
            float bestClass = 1.0f;
            if (numFeatures > 6) {
                float maxClass = 0.0f;
                for (int c = 5; c < numFeatures; c++) {
                    maxClass = Math.max(maxClass, getValue(raw, i, c, numBoxes, numFeatures, channelFirst));
                }
                bestClass = maxClass;
            } else if (numFeatures == 6) {
                bestClass = getValue(raw, i, 5, numBoxes, numFeatures, channelFirst);
            }
            double confidence = sigmoid(objectness) * sigmoid(bestClass);
            if (confidence < properties.getConfThreshold()) {
                continue;
            }
            double x1 = (cx - w / 2.0 - pad[0]) / scale[0];
            double y1 = (cy - h / 2.0 - pad[1]) / scale[0];
            double x2 = (cx + w / 2.0 - pad[0]) / scale[0];
            double y2 = (cy + h / 2.0 - pad[1]) / scale[0];
            Rect rect = ImageUtils.clipRect(new Rect((int) Math.round(x1), (int) Math.round(y1),
                    (int) Math.round(x2 - x1), (int) Math.round(y2 - y1)), original);
            if (rect.width() <= 0 || rect.height() <= 0) {
                continue;
            }
            detections.add(new Detection(rect.x(), rect.y(), rect.width(), rect.height(), confidence));
        }
        tensor.close();
        output.close();

        List<Detection> filtered = nonMaxSuppression(detections, properties.getIouThreshold());
        long postEnd = System.nanoTime();

        log.debug("YOLO timings - preprocess: {} ms, inference: {} ms, post: {} ms",
                (preprocessEnd - start) / 1_000_000.0,
                (inferenceEnd - preprocessEnd) / 1_000_000.0,
                (postEnd - inferenceEnd) / 1_000_000.0);
        return filtered;
    }

    private float getValue(FloatBuffer buffer, int boxIndex, int featureIndex, int numBoxes, int numFeatures, boolean channelFirst) {
        if (channelFirst) {
            return buffer.get(featureIndex * numBoxes + boxIndex);
        }
        return buffer.get(boxIndex * numFeatures + featureIndex);
    }

    private double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private List<Detection> nonMaxSuppression(List<Detection> detections, double threshold) {
        detections.sort(Comparator.comparingDouble(Detection::confidence).reversed());
        List<Detection> selected = new ArrayList<>();
        for (Detection candidate : detections) {
            boolean keep = true;
            for (Detection kept : selected) {
                if (iou(candidate, kept) > threshold) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private double iou(Detection a, Detection b) {
        double ax1 = a.x();
        double ay1 = a.y();
        double ax2 = ax1 + a.width();
        double ay2 = ay1 + a.height();
        double bx1 = b.x();
        double by1 = b.y();
        double bx2 = bx1 + b.width();
        double by2 = by1 + b.height();

        double interX1 = Math.max(ax1, bx1);
        double interY1 = Math.max(ay1, by1);
        double interX2 = Math.min(ax2, bx2);
        double interY2 = Math.min(ay2, by2);
        double interArea = Math.max(0, interX2 - interX1) * Math.max(0, interY2 - interY1);
        double unionArea = a.width() * a.height() + b.width() * b.height() - interArea + 1e-6;
        return interArea / unionArea;
    }

    public record Detection(double x, double y, double width, double height, double confidence) {
        public Rect toRect() {
            return new Rect((int) Math.round(x), (int) Math.round(y), (int) Math.round(width), (int) Math.round(height));
        }
    }
}
