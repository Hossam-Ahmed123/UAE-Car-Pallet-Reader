package com.uae.anpr.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecognitionResponse(
        @Schema(description = "Normalized alphanumeric UAE plate number")
        String plateNumber,
        @Schema(description = "Detected emirate or city name if derivable from the plate text")
        String city,
        @Schema(description = "Alphabetic classification character(s) printed on the plate")
        String plateCharacter,
        @Schema(description = "Numeric component of the plate")
        String carNumber,
        @Schema(description = "Confidence score emitted by OCR")
        double confidence,
        @Schema(description = "Whether the detection reached the configured acceptance threshold")
        boolean accepted) {
}
