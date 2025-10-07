package com.uae.anpr.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecognitionResponse(
        @Schema(description = "Normalized alphanumeric UAE plate number")
        String plateNumber,
        @Schema(description = "Confidence score emitted by OCR")
        double confidence,
        @Schema(description = "Whether the detection reached the configured acceptance threshold")
        boolean accepted) {
}
