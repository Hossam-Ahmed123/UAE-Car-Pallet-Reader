package com.uae.anpr.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RecognitionRequest(
        @Schema(description = "Base64 encoded representation of the plate image", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String imageBase64) {
}
