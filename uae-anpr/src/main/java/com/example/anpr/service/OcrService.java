package com.example.anpr.service;

import com.example.anpr.util.ImageUtils;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.sourceforge.tess4j.TessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final Tesseract tess;

    public OcrService(@Value("${ocr.tessdataPath}") String tessdataPath,
                      @Value("${ocr.lang:eng+ara}") String lang,
                      @Value("${ocr.whitelist}") String whitelist) {
        this.tess = new Tesseract();
        Path tessdataDir = Paths.get(tessdataPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(tessdataDir)) {
            throw new IllegalArgumentException("Invalid tessdata directory: " + tessdataDir);
        }
        this.tess.setDatapath(tessdataDir.toString());
        log.info("Initialized Tesseract with tessdata directory: {}", tessdataDir);
        this.tess.setLanguage(lang);
        this.tess.setTessVariable("user_defined_dpi", "300");
        if (whitelist != null && !whitelist.isBlank()) {
            this.tess.setTessVariable("tessedit_char_whitelist", whitelist);
        }
    }

    private String ocrWith(String whitelist, int psm, BufferedImage bi) {
        try {
            tess.setTessVariable("user_defined_dpi", "300");
            if (whitelist != null && !whitelist.isBlank()) {
                tess.setTessVariable("tessedit_char_whitelist", whitelist);
            }
            tess.setPageSegMode(psm);
            return tess.doOCR(bi);
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        }
    }

    public String ocrDigits(BufferedImage bi) {
        return ocrWith("0123456789", TessAPI.TessPageSegMode.PSM_SINGLE_LINE, bi);
    }

    public String ocrLetters(BufferedImage bi) {
        return ocrWith("ABCDEFGHIJKLMNOPQRSTUVWXYZ", TessAPI.TessPageSegMode.PSM_SINGLE_CHAR, bi);
    }

    public String ocrEmirate(BufferedImage bi) {
        return ocrWith(null, TessAPI.TessPageSegMode.PSM_SINGLE_BLOCK, bi);
    }

    public String doOcr(org.bytedeco.opencv.opencv_core.Mat mat) {
        try {
            return tess.doOCR(ImageUtils.toBufferedImage(mat));
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        }
    }
}
