package com.example.uaecarpalletreader.service.detection;

import com.example.uaecarpalletreader.model.BoundingBox;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Detects one or more license plate regions inside an image. Implementations
 * can rely on deep learning models (e.g. YOLO) or any classical computer
 * vision technique. The interface is intentionally minimal so alternative
 * detectors can be wired in through Spring configuration.
 */
public interface PlateDetector {

    /**
     * @param image input vehicle image
     * @return list of candidate plate regions. The list can be empty when no
     * detections are produced.
     */
    List<BoundingBox> detect(BufferedImage image);
}

