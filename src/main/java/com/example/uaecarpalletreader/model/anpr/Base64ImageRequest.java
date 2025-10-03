package com.example.uaecarpalletreader.model.anpr;

import jakarta.validation.constraints.NotBlank;

public record Base64ImageRequest(@NotBlank String image) {
}
