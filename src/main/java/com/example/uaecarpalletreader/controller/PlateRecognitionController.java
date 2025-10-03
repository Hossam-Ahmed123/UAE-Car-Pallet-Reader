package com.example.uaecarpalletreader.controller;

import com.example.uaecarpalletreader.model.PlateExtractionResponse;
import com.example.uaecarpalletreader.model.PlateExtractionResult;
import com.example.uaecarpalletreader.service.PlateRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/plates")
@Validated
public class PlateRecognitionController {

    private final PlateRecognitionService service;

    public PlateRecognitionController(PlateRecognitionService service) {
        this.service = service;
    }

    @Operation(
            summary = "Extract UAE car plate numbers from uploaded images",
            description = "Accepts one or more vehicle images, runs OCR, and returns the normalized plate numbers.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Plate numbers successfully extracted",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PlateExtractionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    })
    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlateExtractionResponse> extract(
            @Parameter(description = "One or more images containing UAE car plates", required = true)
            @RequestPart("images") List<MultipartFile> images) {
        if (CollectionUtils.isEmpty(images)) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one image must be provided");
        }
        List<PlateExtractionResult> results = images.stream()
                .map(service::extractPlate)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new PlateExtractionResponse(results));
    }
}
