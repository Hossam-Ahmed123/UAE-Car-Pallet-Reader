package com.uae.anpr.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Structured information extracted from a UAE license plate")
public record PlateResult(
        @Schema(description = "Normalized numeric portion of the plate", example = "97344") String number,
        @Schema(description = "Latin letter encoded on the plate", example = "F") String letter,
        @Schema(description = "Matched emirate name", example = "Dubai") String emirate,
        @Schema(description = "Raw OCR diagnostics", example = "STAGE=ROI | DIGITS=97344 | LETTER=F | EMIRATE=Dubai") String rawText) {
}
