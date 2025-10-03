package com.example.uaecarpalletreader.controller;

import com.example.uaecarpalletreader.config.AnprProperties;
import com.example.uaecarpalletreader.model.anpr.AnprHealthResponse;
import com.example.uaecarpalletreader.model.anpr.AnprInferenceResponse;
import com.example.uaecarpalletreader.model.anpr.AnprPlateResponse;
import com.example.uaecarpalletreader.model.anpr.AnprServiceResult;
import com.example.uaecarpalletreader.model.anpr.Base64ImageRequest;
import com.example.uaecarpalletreader.model.anpr.PlateReading;
import com.example.uaecarpalletreader.service.anpr.AnprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
@RequestMapping("/api/anpr")
@Tag(name = "ANPR", description = "Automatic Number Plate Recognition endpoints")
public class AnprController {

    private static final Logger log = LoggerFactory.getLogger(AnprController.class);

    private final AnprProperties properties;
    private final AnprService service;

    public AnprController(AnprProperties properties, AnprService service) {
        this.properties = properties;
        this.service = service;
    }

    @PostMapping(value = "/infer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Run ANPR on an uploaded image", description = "Accepts a single vehicle image and returns detected plates with OCR results.")
    public ResponseEntity<AnprInferenceResponse> inferFromMultipart(@RequestPart("image") MultipartFile image) {
        return runInference(readImageFromMultipart(image));
    }

    @PostMapping(value = "/infer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Run ANPR on a base64 encoded image", description = "Accepts a base64 encoded image and returns detected plates with OCR results.")
    public ResponseEntity<AnprInferenceResponse> inferFromBase64(@Valid @RequestBody Base64ImageRequest request) {
        return runInference(readImageFromBase64(request.image()));
    }

    @GetMapping("/health")
    @Operation(summary = "Retrieve ANPR service health state")
    public ResponseEntity<AnprHealthResponse> health() {
        return ResponseEntity.ok(new AnprHealthResponse(properties.isEnabled(), properties.getModelPath()));
    }

    private ResponseEntity<AnprInferenceResponse> runInference(BufferedImage image) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "ANPR service is disabled");
        }
        try {
            AnprServiceResult result = service.detectAndRead(image);
            AnprInferenceResponse response = new AnprInferenceResponse(mapPlates(result), result.modelTimeMs(), result.ocrTimeMs());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            log.error("ANPR inference failed", ex);
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private List<AnprPlateResponse> mapPlates(AnprServiceResult result) {
        return result.plates().stream()
                .map(this::toPlateResponse)
                .collect(Collectors.toList());
    }

    private AnprPlateResponse toPlateResponse(PlateReading reading) {
        List<Integer> bbox = List.of(
                reading.boundingBox().x(),
                reading.boundingBox().y(),
                reading.boundingBox().width(),
                reading.boundingBox().height());
        return new AnprPlateResponse(bbox, reading.text(), reading.confidence());
    }

    private BufferedImage readImageFromMultipart(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Image file is required");
        }
        try (var inputStream = image.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Unable to decode provided image");
            }
            return bufferedImage;
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Failed to read uploaded image", ex);
        }
    }

    private BufferedImage readImageFromBase64(String encodedImage) {
        if (!StringUtils.hasText(encodedImage)) {
            throw new ResponseStatusException(BAD_REQUEST, "Base64 image data is required");
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(encodedImage);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid Base64 image data", ex);
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Unable to decode Base64 image data");
            }
            return bufferedImage;
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Failed to read Base64 image data", ex);
        }
    }
}
