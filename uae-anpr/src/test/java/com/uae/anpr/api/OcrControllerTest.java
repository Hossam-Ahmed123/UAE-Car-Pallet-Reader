package com.uae.anpr.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uae.anpr.api.dto.RecognitionRequest;
import com.uae.anpr.api.dto.RecognitionResponse;
import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.config.AnprProperties.OcrProperties;
import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.parser.UaePlateParser;
import com.uae.anpr.service.pipeline.RecognitionPipeline;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class OcrControllerTest {

    @Test
    void rejectsResultWithoutClassification() {
        RecognitionPipeline pipeline = mock(RecognitionPipeline.class);
        when(pipeline.recognize(any())).thenReturn(Optional.of(new OcrResult("45158", 0.995)));

        AnprProperties properties = new AnprProperties(null, new OcrProperties("eng", 0.9, false, null, null));
        UaePlateParser parser = new UaePlateParser();
        OcrController controller = new OcrController(pipeline, properties, parser);

        byte[] payload = {0x01, 0x02, 0x03};
        RecognitionRequest request = new RecognitionRequest(Base64.getEncoder().encodeToString(payload));

        ResponseEntity<RecognitionResponse> response = controller.recognize(request);

        assertNotNull(response.getBody());
        assertFalse(response.getBody().accepted(), "Result lacking classification should not be accepted");
    }
}

