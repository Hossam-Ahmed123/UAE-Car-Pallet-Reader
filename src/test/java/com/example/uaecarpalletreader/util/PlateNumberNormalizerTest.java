package com.example.uaecarpalletreader.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlateNumberNormalizerTest {

    @Test
    void shouldNormalizePlateWithDubaiPrefix() {
        String normalized = PlateNumberNormalizer.normalize("Dubai bb 19849\n");
        assertThat(normalized).isEqualTo("BB 19849");
    }

    @Test
    void shouldNormalizeWithSpecialCharacters() {
        String normalized = PlateNumberNormalizer.normalize("F-97344");
        assertThat(normalized).isEqualTo("F 97344");
    }

    @Test
    void shouldReturnNullForEmpty() {
        assertThat(PlateNumberNormalizer.normalize("   ")).isNull();
    }
}
