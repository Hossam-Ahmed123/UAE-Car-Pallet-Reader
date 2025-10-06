package com.uae.anpr.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response returned by the plate recognition endpoint")
public record PlateResponse(
        @ArraySchema(arraySchema = @Schema(description = "Recognized plate candidates"),
                schema = @Schema(implementation = PlateResult.class))
        List<PlateResult> results) {
    public static PlateResponse of(PlateResult result) {
        return new PlateResponse(List.of(result));
    }
}
