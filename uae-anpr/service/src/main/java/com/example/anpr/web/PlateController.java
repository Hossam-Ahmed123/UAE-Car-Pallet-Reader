package com.example.anpr.web;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.exception.PlateProcessingException;
import com.example.anpr.service.PlateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/plates")
public class PlateController {

    private static final Logger log = LoggerFactory.getLogger(PlateController.class);

    private final PlateService plateService;

    public PlateController(PlateService plateService) {
        this.plateService = plateService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlateResponse> recognize(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            throw new PlateProcessingException("Uploaded image is empty", null);
        }
        try {
            PlateResponse response = plateService.recognize(image.getBytes());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to read uploaded image", e);
            throw new PlateProcessingException("Failed to read uploaded image", e);
        }
    }
}
