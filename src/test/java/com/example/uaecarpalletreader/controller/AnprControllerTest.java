package com.example.uaecarpalletreader.controller;

import com.example.uaecarpalletreader.model.BoundingBox;
import com.example.uaecarpalletreader.model.anpr.AnprServiceResult;
import com.example.uaecarpalletreader.model.anpr.PlateReading;
import com.example.uaecarpalletreader.service.anpr.AnprService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnprControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnprService anprService;

    @Test
    void inferReturnsAtLeastOnePlate() throws Exception {
        when(anprService.detectAndRead(any(BufferedImage.class)))
                .thenReturn(new AnprServiceResult(
                        List.of(new PlateReading(new BoundingBox(10, 20, 100, 40), "A 12345", 0.95)),
                        23,
                        15));

        BufferedImage plateImage = new BufferedImage(200, 100, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = plateImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 200, 100);
            graphics.setColor(Color.BLACK);
            graphics.drawString("A 12345", 50, 50);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(plateImage, "png", outputStream);
        MockMultipartFile mockFile = new MockMultipartFile(
                "image",
                "plate.png",
                MediaType.IMAGE_PNG_VALUE,
                outputStream.toByteArray());

        mockMvc.perform(multipart("/api/anpr/infer").file(mockFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plates").isArray())
                .andExpect(jsonPath("$.plates.length()", greaterThanOrEqualTo(1)));
    }
}
