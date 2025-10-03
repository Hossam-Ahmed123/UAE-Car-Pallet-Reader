package com.example.uaecarpalletreader.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Plate extraction output for a single uploaded image")
public record PlateExtractionResult(
        @Schema(description = "Original name of the processed file", example = "maxima.jpg") String fileName,
        @Schema(description = "Raw OCR output before normalization", example = "F\\n97344") String rawText,
        @Schema(description = "Normalized UAE plate number", example = "F 97344") String normalizedPlate) {
}
