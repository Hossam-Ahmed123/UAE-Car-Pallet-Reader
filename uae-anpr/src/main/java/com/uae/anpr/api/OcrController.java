package com.uae.anpr.api;

import com.uae.anpr.api.dto.RecognitionRequest;
import com.uae.anpr.api.dto.RecognitionResponse;
import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.pipeline.RecognitionPipeline;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/anpr", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "UAE Automatic Number Plate Recognition")
@Validated
public class OcrController {

    private final RecognitionPipeline pipeline;
    private final AnprProperties properties;

    public OcrController(RecognitionPipeline pipeline, AnprProperties properties) {
        this.pipeline = pipeline;
        this.properties = properties;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recognize a UAE car plate number", description = "Performs algorithmic enhancement and OCR",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recognition result",
                            content = @Content(schema = @Schema(implementation = RecognitionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid payload")
            })
    public ResponseEntity<RecognitionResponse> recognize(@RequestBody RecognitionRequest request) {
        byte[] imageBytes = Base64.getDecoder().decode(request.imageBase64());
        Optional<OcrResult> result = pipeline.recognize(imageBytes);
        if (result.isEmpty()) {
            return ResponseEntity.ok(new RecognitionResponse(null, 0.0, false));
        }
        OcrResult ocrResult = result.get();
        boolean accepted = ocrResult.confidence() >= properties.ocr().confidenceThreshold();
        return ResponseEntity.ok(new RecognitionResponse(ocrResult.text(), ocrResult.confidence(), accepted));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<RecognitionResponse> handleIllegalArgument(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new RecognitionResponse(null, 0.0, false));
    }
}
