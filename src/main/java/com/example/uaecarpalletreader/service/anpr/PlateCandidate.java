package com.example.uaecarpalletreader.service.anpr;

import com.example.uaecarpalletreader.model.BoundingBox;

import java.awt.image.BufferedImage;

public record PlateCandidate(BoundingBox boundingBox, BufferedImage roi, double confidence) {
}
