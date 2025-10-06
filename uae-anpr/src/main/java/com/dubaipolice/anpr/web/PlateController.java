package com.dubaipolice.anpr.web;

import com.dubaipolice.anpr.dto.PlateResponse;
import com.dubaipolice.anpr.dto.PlateResult;
import com.dubaipolice.anpr.service.PlateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
@RestController
@RequestMapping("/api/v1/plates")
@Tag(name = "Plate recognition")
public class PlateController {

    private final PlateService plateService;
    private static final Logger log = LoggerFactory.getLogger(PlateController.class);

    public PlateController(PlateService plateService) {
        this.plateService = plateService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Recognize a UAE license plate",
            description = "Accepts a vehicle or plate image and extracts the emirate, letter and number using OCR.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Plate recognized"),
                    @ApiResponse(responseCode = "400", description = "Invalid image format"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public ResponseEntity<PlateResponse> recognize(
            @Parameter(description = "Vehicle or close-up plate image (PNG or JPEG).", required = true)
            @RequestPart("image") MultipartFile image) {

        try {
            if (image.isEmpty()) {
                return ResponseEntity.badRequest().body(PlateResponse.of(
                        new PlateResult("", "", "Unknown", "Empty image file")));
            }

            if (!isSupportedFormat(image.getContentType())) {
                return ResponseEntity.badRequest().body(PlateResponse.of(
                        new PlateResult("", "", "Unknown", "Unsupported image format: " + image.getContentType())));
            }

            PlateResponse response = plateService.recognize(image.getBytes());
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading image file", e);
            return ResponseEntity.internalServerError().body(PlateResponse.of(
                    new PlateResult("", "", "Unknown", "Error processing image: " + e.getMessage())));
        } catch (Exception e) {
            log.error("Unexpected error during plate recognition", e);
            return ResponseEntity.internalServerError().body(PlateResponse.of(
                    new PlateResult("", "", "Unknown", "Internal server error")));
        }
    }

    private boolean isSupportedFormat(String contentType) {
        return contentType != null &&
                (contentType.startsWith("image/jpeg") ||
                        contentType.startsWith("image/png") ||
                        contentType.startsWith("image/jpg"));
    }
}