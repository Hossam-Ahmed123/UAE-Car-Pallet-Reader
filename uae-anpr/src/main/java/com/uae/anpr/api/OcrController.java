package com.uae.anpr.api;

import com.uae.anpr.api.dto.RecognitionRequest;
import com.uae.anpr.api.dto.RecognitionResponse;
import com.uae.anpr.config.AnprProperties;
import com.uae.anpr.service.ocr.TesseractOcrEngine.OcrResult;
import com.uae.anpr.service.parser.UaePlateParser;
import com.uae.anpr.service.parser.UaePlateParser.PlateBreakdown;
import com.uae.anpr.service.pipeline.RecognitionPipeline;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/v1/anpr", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "UAE Automatic Number Plate Recognition")
@Validated
public class OcrController {

    private final RecognitionPipeline pipeline;
    private final AnprProperties properties;
    private final UaePlateParser plateParser;

    public OcrController(RecognitionPipeline pipeline, AnprProperties properties, UaePlateParser plateParser) {
        this.pipeline = pipeline;
        this.properties = properties;
        this.plateParser = plateParser;
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
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping(value = "/recognize/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Recognize a UAE car plate number from an uploaded image",
            description = "Accepts a multipart image file, performs enhancement and OCR",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recognition result",
                            content = @Content(schema = @Schema(implementation = RecognitionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid payload")
            })
    public ResponseEntity<RecognitionResponse> recognizeFile(@RequestPart("image") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Uploaded image must not be empty");
        }
        try {
            Optional<OcrResult> result = pipeline.recognize(image.getBytes());
            return ResponseEntity.ok(toResponse(result));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded image", ex);
        }
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<RecognitionResponse> handleIllegalArgument(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new RecognitionResponse(null, null, null, null, 0.0, false));
    }

    private RecognitionResponse toResponse(Optional<OcrResult> result) {
        if (result.isEmpty()) {
            return new RecognitionResponse(null, null, null, null, 0.0, false);
        }
        OcrResult ocrResult = result.get();
        PlateBreakdown breakdown = plateParser.parse(ocrResult.text());
        boolean hasClassification = breakdown.plateCharacter() != null && !breakdown.plateCharacter().isBlank();
        boolean hasDigits = breakdown.carNumber() != null && !breakdown.carNumber().isBlank();
        boolean accepted = ocrResult.confidence() >= properties.ocr().confidenceThreshold() && hasClassification && hasDigits;
        return new RecognitionResponse(
                ocrResult.text(),
                breakdown.city(),
                breakdown.plateCharacter(),
                breakdown.carNumber(),
                ocrResult.confidence(),
                accepted);
    }
}
