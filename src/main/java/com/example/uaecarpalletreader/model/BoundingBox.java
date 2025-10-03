package com.example.uaecarpalletreader.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Simple bounding box representation used to describe a detected plate region
 * inside a source image. Coordinates follow the image pixel grid with the
 * origin located in the top-left corner.
 */
@Schema(description = "Axis-aligned rectangle describing a detected plate region")
public record BoundingBox(
        @Schema(description = "X coordinate of the top-left corner", example = "42") int x,
        @Schema(description = "Y coordinate of the top-left corner", example = "128") int y,
        @Schema(description = "Bounding box width in pixels", example = "180") int width,
        @Schema(description = "Bounding box height in pixels", example = "60") int height) {

    public BoundingBox {
        if (width <= 0) {
            throw new IllegalArgumentException("Bounding box width must be positive");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Bounding box height must be positive");
        }
    }
}

