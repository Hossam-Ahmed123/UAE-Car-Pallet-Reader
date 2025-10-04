package com.example.anpr.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmirateParserTest {

    private final EmirateParser parser = new EmirateParser();

    @Test
    void detectsDubaiFromEnglish() {
        EmirateParser.ParsedData data = parser.parse("Dubai F 12345");
        assertThat(data.emirate()).isEqualTo("Dubai");
        assertThat(data.letter()).isEqualTo("F");
        assertThat(data.number()).isEqualTo("12345");
    }

    @Test
    void detectsAjmanFromArabic() {
        EmirateParser.ParsedData data = parser.parse("عجمان 6789 A");
        assertThat(data.emirate()).isEqualTo("Ajman");
        assertThat(data.number()).isEqualTo("6789");
        assertThat(data.letter()).isEqualTo("A");
    }

    @Test
    void gracefullyHandlesNull() {
        EmirateParser.ParsedData data = parser.parse(null);
        assertThat(data.emirate()).isNull();
        assertThat(data.letter()).isNull();
        assertThat(data.number()).isNull();
    }
}
