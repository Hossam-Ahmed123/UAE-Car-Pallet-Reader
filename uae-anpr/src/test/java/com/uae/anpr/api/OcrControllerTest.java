package com.uae.anpr.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.uae.anpr.api.dto.RecognitionResponse;
import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.config.AnprProperties.OcrProperties;
import com.uae.anpr.config.AnprProperties.ResourceSet;
import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.parser.UaePlateParser;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcrControllerTest {

    private OcrController controller;

    @BeforeEach
    void setUp() {
        AnprProperties properties = new AnprProperties(
                new ResourceSet(null, null, null, null),
                new OcrProperties("eng", 0.85, false, null, null));
        controller = new OcrController(null, properties, new UaePlateParser());
    }

    @Test
    void toResponsePrefersDigitsWhenAvailable() {
        RecognitionResponse response = controller.toResponse(Optional.of(new OcrResult("45158X", 0.97)));

        assertEquals("45158", response.plateNumber());
        assertEquals("X", response.plateCharacter());
        assertEquals(0.97, response.confidence());
    }

    @Test
    void toResponseFallsBackToRawTextWhenDigitsUnavailable() {
        RecognitionResponse response = controller.toResponse(Optional.of(new OcrResult("DXB", 0.91)));

        assertEquals("DXB", response.plateNumber());
        assertNull(response.plateCharacter());
        assertEquals(0.91, response.confidence());
    }

    @Test
    void toResponseReturnsEmptyPayloadWhenResultMissing() {
        RecognitionResponse response = controller.toResponse(Optional.empty());

        assertNull(response.plateNumber());
        assertNull(response.plateCharacter());
        assertEquals(0.0, response.confidence());
    }
}
