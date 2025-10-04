package com.example.anpr.dto;

import java.util.List;

public record PlateResponse(java.util.List<PlateResult> results) {
    public static PlateResponse of(PlateResult r) { return new PlateResponse(java.util.List.of(r)); }
}
