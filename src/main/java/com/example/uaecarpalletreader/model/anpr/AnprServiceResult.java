package com.example.uaecarpalletreader.model.anpr;

import java.util.List;

public record AnprServiceResult(List<PlateReading> plates, long modelTimeMs, long ocrTimeMs) {
}
