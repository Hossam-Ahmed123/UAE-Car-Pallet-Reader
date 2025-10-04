package com.example.anpr.dto;

import java.util.List;

public class PlateResponse {

    private List<PlateResult> results;

    public PlateResponse() {
    }

    public PlateResponse(List<PlateResult> results) {
        this.results = results;
    }

    public List<PlateResult> getResults() {
        return results;
    }

    public void setResults(List<PlateResult> results) {
        this.results = results;
    }
}
