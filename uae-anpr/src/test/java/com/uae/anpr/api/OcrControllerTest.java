package com.uae.anpr.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uae.anpr.api.dto.RecognitionResponse;
import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.config.AnprProperties.OcrProperties;
import com.uae.anpr.config.AnprProperties.ResourceSet;
import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.parser.UaePlateParser;
import com.uae.anpr.service.parser.UaePlateParser.PlateBreakdown;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcrControllerTest {

    private AnprProperties properties;
    private OcrController controller;

    @BeforeEach
    void setUp() {
        properties = new AnprProperties(
                new ResourceSet(null, null, null, null),
                new OcrProperties("eng", 0.85, false, null, null));
        controller = new OcrController(null, properties, new UaePlateParser());
    }

    @Test
    void toResponseUsesNormalizedPlateTextWhenAvailable() {
        RecognitionResponse response = controller.toResponse(Optional.of(new OcrResult("DUBAIF97344", 0.97)));

        assertEquals("DUBAIF97344", response.plateNumber());
        assertEquals("Dubai", response.city());
        assertEquals("F", response.plateCharacter());
        assertEquals("97344", response.carNumber());
        assertEquals(0.97, response.confidence());
        assertTrue(response.accepted());
    }

    @Test
    void toResponseReturnsRawTextWhenNoDigitsDetected() {
        RecognitionResponse response = controller.toResponse(Optional.of(new OcrResult("DXB", 0.91)));

        assertEquals("DXB", response.plateNumber());
        assertNull(response.plateCharacter());
        assertNull(response.carNumber());
        assertEquals(0.91, response.confidence());
        assertTrue(response.accepted());
    }

    @Test
    void toResponseFallsBackToDigitsWhenTextBlank() {
        OcrController fallbackController = new OcrController(null, properties, new UaePlateParser() {
            @Override
            public PlateBreakdown parse(String plateText) {
                return new PlateBreakdown(null, null, "97344");
            }
        });

        RecognitionResponse response = fallbackController.toResponse(Optional.of(new OcrResult("   ", 0.91)));

        assertEquals("97344", response.plateNumber());
        assertEquals("97344", response.carNumber());
    }

    @Test
    void toResponseReturnsEmptyPayloadWhenResultMissing() {
        RecognitionResponse response = controller.toResponse(Optional.empty());

        assertNull(response.plateNumber());
        assertNull(response.plateCharacter());
        assertEquals(0.0, response.confidence());
        assertFalse(response.accepted());
    }

    @Test
    void toResponseRejectsResultsBelowThreshold() {
        RecognitionResponse response = controller.toResponse(Optional.of(new OcrResult("45158", 0.50)));

        assertFalse(response.accepted());
    }
}
