package com.example.uaecarpalletreader.model.anpr;

import com.example.uaecarpalletreader.model.BoundingBox;

public record PlateReading(BoundingBox boundingBox, String text, double confidence) {
}
