package com.example.uaecarpalletreader.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aggregated response for a plate extraction request")
public record PlateExtractionResponse(
        @Schema(description = "Collection of per-image extraction results")
        List<PlateExtractionResult> results) {
}
