package com.example.anpr.web;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.service.PlateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/plates")
@Tag(name = "Plate recognition")
public class PlateController {

    private final PlateService plateService;

    public PlateController(PlateService plateService) {
        this.plateService = plateService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Recognize a UAE license plate",
            description = "Accepts a vehicle or plate image and extracts the emirate, letter and number using OCR.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Plate recognized",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = PlateResponse.class)))
            }
    )
    public PlateResponse recognize(
            @Parameter(description = "Vehicle or close-up plate image (PNG or JPEG).", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("image") MultipartFile image) {
        return plateService.recognize(image.getBytes());
    }
}
