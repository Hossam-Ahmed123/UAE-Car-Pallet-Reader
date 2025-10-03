package com.example.uaecarpalletreader.model.anpr;

import java.util.List;

public record AnprInferenceResponse(List<AnprPlateResponse> plates, long modelTimeMs, long ocrTimeMs) {
}
