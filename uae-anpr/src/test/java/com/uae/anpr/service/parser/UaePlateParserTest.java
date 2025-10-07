package com.uae.anpr.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.uae.anpr.service.parser.UaePlateParser.PlateBreakdown;
import org.junit.jupiter.api.Test;

class UaePlateParserTest {

    private final UaePlateParser parser = new UaePlateParser();

    @Test
    void parsesDubaiPlateWithCityAndLetter() {
        PlateBreakdown breakdown = parser.parse("DUBAIA12345");
        assertEquals("Dubai", breakdown.city());
        assertEquals("A", breakdown.plateCharacter());
        assertEquals("12345", breakdown.carNumber());
    }

    @Test
    void parsesAbuDhabiPlate() {
        PlateBreakdown breakdown = parser.parse("ABUDHABIA54321");
        assertEquals("Abu Dhabi", breakdown.city());
        assertEquals("A", breakdown.plateCharacter());
        assertEquals("54321", breakdown.carNumber());
    }

    @Test
    void fallsBackToSingleLetterWhenCityUnknown() {
        PlateBreakdown breakdown = parser.parse("M12345");
        assertNull(breakdown.city());
        assertEquals("M", breakdown.plateCharacter());
        assertEquals("12345", breakdown.carNumber());
    }

    @Test
    void supportsLetterAfterDigits() {
        PlateBreakdown breakdown = parser.parse("1234A");
        assertNull(breakdown.city());
        assertEquals("A", breakdown.plateCharacter());
        assertEquals("1234", breakdown.carNumber());
    }

    @Test
    void resolvesCityFromNoisyPrefix() {
        PlateBreakdown breakdown = parser.parse("TH59744");
        assertEquals("Sharjah", breakdown.city());
        assertEquals("TH", breakdown.plateCharacter());
        assertEquals("59744", breakdown.carNumber());
    }

    @Test
    void returnsEmptyBreakdownForInvalidInput() {
        PlateBreakdown breakdown = parser.parse("   ");
        assertNull(breakdown.city());
        assertNull(breakdown.plateCharacter());
        assertNull(breakdown.carNumber());
    }
}
