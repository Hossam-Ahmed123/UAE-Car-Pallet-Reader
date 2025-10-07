package com.uae.anpr.service.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.sourceforge.tess4j.ITesseract;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TesseractOcrEngineTest {

    @Test
    void usesMeanConfidenceFromTesseract() throws Exception {
        ITesseract tess = Mockito.mock(ITesseract.class);
        Mockito.when(tess.doOCR(ArgumentMatchers.any())).thenReturn("abc123");
        Mockito.when(tess.getMeanConfidence()).thenReturn(73);

        TesseractOcrEngine engine = new TesseractOcrEngine(tess);
        Mat mat = new Mat(1, 1, opencv_core.CV_8UC1);

        Optional<TesseractOcrEngine.OcrResult> result = engine.recognize(mat);

        assertTrue(result.isPresent());
        assertEquals("ABC123", result.get().text());
        assertEquals(0.73, result.get().confidence(), 1e-6);
    }

    @Test
    void returnsZeroConfidenceWhenMeanConfidenceUnavailable() throws Exception {
        ITesseract tess = Mockito.mock(ITesseract.class);
        Mockito.when(tess.doOCR(ArgumentMatchers.any())).thenReturn("DXB");
        Mockito.when(tess.getMeanConfidence()).thenThrow(new UnsupportedOperationException("no confidence"));

        TesseractOcrEngine engine = new TesseractOcrEngine(tess);
        Mat mat = new Mat(1, 1, opencv_core.CV_8UC1);

        Optional<TesseractOcrEngine.OcrResult> result = engine.recognize(mat);

        assertTrue(result.isPresent());
        assertEquals(0.0, result.get().confidence(), 1e-6);
    }

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
