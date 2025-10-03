package com.example.uaecarpalletreader.service.detection;

import com.example.uaecarpalletreader.model.BoundingBox;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Default detector that simply returns the whole image as a single candidate
 * plate region. This acts as a safe fallback when no dedicated detector
 * (e.g. YOLO model) is available.
 */
public class NoOpPlateDetector implements PlateDetector {

    @Override
    public List<BoundingBox> detect(BufferedImage image) {
        if (image == null) {
            return List.of();
        }
        return List.of(new BoundingBox(0, 0, image.getWidth(), image.getHeight()));
    }
}

