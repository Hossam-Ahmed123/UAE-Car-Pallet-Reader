package com.example.uaecarpalletreader.service;

import com.example.uaecarpalletreader.model.PlateExtractionResult;
import com.example.uaecarpalletreader.util.PlateNumberNormalizer;
import com.example.uaecarpalletreader.util.PlateNumberNormalizer.NormalizedPlate;
import jakarta.annotation.PostConstruct;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.io.InputStream;

@Service
public class PlateRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(PlateRecognitionService.class);

    private final Tesseract tesseract;
    private final String language;

    public PlateRecognitionService(Tesseract tesseract,
                                   @Value("${tesseract.language:eng}") String language) {
        this.tesseract = tesseract;
        this.language = language;
    }

    @PostConstruct
    void logTesseractInfo() {
        log.info("Tesseract OCR initialized with language: {}", language);
    }

    public PlateExtractionResult extractPlate(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "image";
        }

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                throw new IllegalArgumentException("Unsupported image type for file: " + fileName);
            }

            BufferedImage preprocessed = preprocess(bufferedImage);
            String rawText = tesseract.doOCR(preprocessed);
            NormalizedPlate normalized = PlateNumberNormalizer.normalize(rawText);
            return new PlateExtractionResult(
                    fileName,
                    rawText != null ? rawText.trim() : null,
                    normalized.normalizedPlate(),
                    normalized.city(),
                    normalized.letters(),
                    normalized.number());
        } catch (IOException e) {
            log.error("Failed to read image {}", fileName, e);
            throw new IllegalStateException("Failed to read image " + fileName, e);
        } catch (TesseractException e) {
            log.error("Tesseract OCR failed for {}", fileName, e);
            throw new IllegalStateException("Failed to extract text from " + fileName, e);
        } catch (Error e) {
            if ("Invalid memory access".equalsIgnoreCase(e.getMessage())) {
                String message = String.format(
                        "Tesseract native layer failed while processing %s. Verify that the tessdata directory contains %s.traineddata.",
                        fileName, language);
                log.error(message, e);
                throw new IllegalStateException(message, e);
            }
            throw e;
        }
    }

    private BufferedImage preprocess(BufferedImage input) {
        BufferedImage grayscale = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayscale.createGraphics();
        g.drawImage(input, 0, 0, null);
        g.dispose();

        RescaleOp rescaleOp = new RescaleOp(1.2f, 15, null);
        rescaleOp.filter(grayscale, grayscale);

        return grayscale;
    }
}
