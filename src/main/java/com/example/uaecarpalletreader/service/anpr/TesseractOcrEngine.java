package com.example.uaecarpalletreader.service.anpr;

import com.example.uaecarpalletreader.config.AnprProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.Locale;

@Component
public class TesseractOcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    private final Tesseract tesseract;

    public TesseractOcrEngine(AnprProperties properties) {
        this.tesseract = new Tesseract();
        String tessdataPath = properties.getTessdataPath();
        if (tessdataPath != null && !tessdataPath.isBlank()) {
            log.info("Configuring Tesseract data path: {}", tessdataPath);
            tesseract.setDatapath(tessdataPath);
        }
        String languages = properties.getLanguages();
        if (languages == null || languages.isBlank()) {
            languages = "ara+eng";
        }
        tesseract.setLanguage(languages);
    }

    public String read(BufferedImage roi) {
        try {
            String raw = tesseract.doOCR(roi);
            if (raw == null) {
                return "";
            }
            return raw.replaceAll("\\s+", " ").trim();
        } catch (TesseractException ex) {
            String message = String.format(Locale.ROOT, "Failed to run OCR on ROI: %s", ex.getMessage());
            log.error(message, ex);
            throw new IllegalStateException(message, ex);
        }
    }
}
