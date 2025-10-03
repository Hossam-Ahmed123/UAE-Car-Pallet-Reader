package com.example.uaecarpalletreader.model.anpr;

import java.util.List;

public record AnprPlateResponse(List<Integer> bbox, String text, double confidence) {
}
