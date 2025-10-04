package com.example.anpr.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OcrServiceTest {

    @Test
    void postProcessCleansAndUpperCases() {
        String cleaned = OcrService.postProcessText(" dubai f-12345 ");
        assertThat(cleaned).isEqualTo("DUBAI F 12345");
    }

    @Test
    void postProcessReturnsNullWhenInputNull() {
        assertThat(OcrService.postProcessText(null)).isNull();
    }
}
