package com.example.anpr.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Structured information extracted from a UAE license plate")
public record PlateResult(
        @Schema(description = "Normalized numeric portion of the plate", example = "97344") String number,
        @Schema(description = "Latin letter encoded on the plate", example = "F") String letter,
        @Schema(description = "Matched emirate name", example = "Dubai") String emirate,
        @Schema(description = "Raw OCR text used for debugging", example = "EMIRATE_RAW=Dubai | DIGITS_RAW=97344 | LETTERS_TRIED=F")
        String rawText) {}
