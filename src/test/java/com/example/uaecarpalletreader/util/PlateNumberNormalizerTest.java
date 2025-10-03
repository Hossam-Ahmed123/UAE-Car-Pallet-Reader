package com.example.uaecarpalletreader.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlateNumberNormalizerTest {

    @Test
    void shouldNormalizePlateWithDubaiPrefix() {
        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize("Dubai bb 19849\n");
        assertThat(normalized.normalizedPlate()).isEqualTo("BB 19849");
        assertThat(normalized.city()).isEqualTo("Dubai");
        assertThat(normalized.characters()).isEqualTo("BB");
        assertThat(normalized.number()).isEqualTo("19849");
    }

    @Test
    void shouldNormalizeWithSpecialCharacters() {
        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize("F-97344");
        assertThat(normalized.normalizedPlate()).isEqualTo("F 97344");
        assertThat(normalized.city()).isNull();
        assertThat(normalized.characters()).isEqualTo("F");
        assertThat(normalized.number()).isEqualTo("97344");
    }

    @Test
    void shouldReturnNullForEmpty() {
        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize("   ");
        assertThat(normalized.normalizedPlate()).isNull();
        assertThat(normalized.city()).isNull();
        assertThat(normalized.characters()).isNull();
        assertThat(normalized.number()).isNull();
    }

    @Test
    void shouldExtractAjmanCityFromAbbreviation() {
        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize("Ajm 12345");
        assertThat(normalized.city()).isEqualTo("Ajman");
        assertThat(normalized.normalizedPlate()).isEqualTo("12345");
        assertThat(normalized.number()).isEqualTo("12345");
        assertThat(normalized.characters()).isNull();
    }

    @Test
    void shouldCollapseSingleDigitSequencesIntoNumber() {
        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize("Sharjah B 1 2 3 4");
        assertThat(normalized.city()).isEqualTo("Sharjah");
        assertThat(normalized.normalizedPlate()).isEqualTo("B 1234");
        assertThat(normalized.characters()).isEqualTo("B");
        assertThat(normalized.number()).isEqualTo("1234");
    }

    @Test
    void shouldReturnNullWhenOnlyNoisySingleDigitTokensDetected() {
        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize("foo 1 a 2 b 3 c 4 d 5");
        assertThat(normalized.normalizedPlate()).isNull();
        assertThat(normalized.number()).isNull();
        assertThat(normalized.characters()).isNull();
        assertThat(normalized.city()).isNull();
    }

    @Test
    void shouldPreferPlausibleTokenWindowAroundDigits() {
        String rawText = """
                I ie Ve
                i ij ' ree
                ~ a —_— a
                SS | Se
                a (4 a
                Soe Sa = a  .
                a
                eee 19949 BY
                a
                oe = oe
                TEES aL nn
                " ROR! | ESOS rs og
                """;

        PlateNumberNormalizer.NormalizedPlate normalized = PlateNumberNormalizer.normalize(rawText);

        assertThat(normalized.normalizedPlate()).isEqualTo("BY 19949");
        assertThat(normalized.characters()).isEqualTo("BY");
        assertThat(normalized.number()).isEqualTo("19949");
    }
}
