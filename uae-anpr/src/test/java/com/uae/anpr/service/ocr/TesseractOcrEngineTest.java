package com.uae.anpr.service.ocr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TesseractOcrEngineTest {

    @Nested
    @DisplayName("shouldForceNumericMode")
    class ShouldForceNumericMode {

        @Test
        void returnsFalseWhenWhitelistContainsLetters() {
            assertFalse(TesseractOcrEngine.shouldForceNumericMode("0123ABZ"));
        }

        @Test
        void returnsTrueForDigitsOnlyWhitelist() {
            assertTrue(TesseractOcrEngine.shouldForceNumericMode("0123456789"));
        }

        @Test
        void returnsFalseForEmptyWhitelist() {
            assertFalse(TesseractOcrEngine.shouldForceNumericMode("   "));
        }
    }
}
