package com.example.anpr.web;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.service.PlateService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/plates")
public class PlateController {

    private final PlateService plateService;

    public PlateController(PlateService plateService) {
        this.plateService = plateService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlateResponse recognize(@RequestPart("image") MultipartFile image) throws Exception {
        return plateService.recognize(image.getBytes());
    }
}
