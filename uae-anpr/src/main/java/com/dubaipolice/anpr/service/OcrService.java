package com.dubaipolice.anpr.service;

import com.dubaipolice.anpr.util.ImageUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.sourceforge.tess4j.TessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;

import static com.dubaipolice.anpr.util.ImageUtils.readMat;
import static com.dubaipolice.anpr.util.ImageUtils.toBufferedImage;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final Tesseract tess;



    private Path resolveTessdataPath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path tessdataDir = Paths.get(configuredPath).toAbsolutePath().normalize();
            if (Files.isDirectory(tessdataDir)) {
                return tessdataDir;
            }
            log.warn("Configured tessdata directory '{}' does not exist; falling back to embedded tessdata", tessdataDir);
        }

        Path extracted = LoadLibs.extractTessResources("tessdata").toPath();
        if (!Files.isDirectory(extracted)) {
            throw new IllegalStateException("Unable to locate tessdata resources under " + extracted);
        }
        return extracted;
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
            return tess.doOCR(toBufferedImage(mat));
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed", e);
        }
    }
    public OcrService(@Value("${ocr.tessdataPath:}") String tessdataPath,
                      @Value("${ocr.lang:eng+ara}") String lang,
                      @Value("${ocr.whitelist}") String whitelist) {
        this.tess = new Tesseract();
        Path tessdataDir = resolveTessdataPath(tessdataPath);
        this.tess.setDatapath(tessdataDir.toString());
        log.info("Initialized Tesseract with tessdata directory: {}", tessdataDir);
        this.tess.setLanguage(lang);

        // Enhanced OCR configuration
        this.tess.setTessVariable("user_defined_dpi", "300");
        this.tess.setTessVariable("tessedit_pageseg_mode", "7"); // Single text line
        this.tess.setTessVariable("tessedit_char_blacklist", "!@#$%^&*()_+-=[]{}|;:'\",.<>?/");

        if (whitelist != null && !whitelist.isBlank()) {
            this.tess.setTessVariable("tessedit_char_whitelist", whitelist);
        }
    }

    private String ocrWith(String whitelist, int psm, BufferedImage bi) {
        try {
            // Preprocess the image for better OCR
            BufferedImage processedImage = preprocessOCRImage(bi);

            tess.setTessVariable("user_defined_dpi", "300");
            if (whitelist != null && !whitelist.isBlank()) {
                tess.setTessVariable("tessedit_char_whitelist", whitelist);
            }
            tess.setPageSegMode(psm);
            String result = tess.doOCR(processedImage);
            return cleanOCRText(result);
        } catch (TesseractException e) {
            log.warn("OCR failed for PSM {}: {}", psm, e.getMessage());
            return "";
        }
    }

// Update the preprocessOCRImage method in OcrService.java:

    private BufferedImage preprocessOCRImage(BufferedImage original) {
        try {
            // Convert to Mat
            Mat mat = ImageUtils.readMat(imageToByteArray(original));

            // Convert to grayscale for OCR
            Mat gray = ImageUtils.toGray(mat);

            // Resize if too small
            if (gray.rows() < 50) {
                gray = ImageUtils.resize(gray, 2.0);
            }

            // Simple contrast enhancement
            opencv_imgproc.equalizeHist(gray, gray);

            // Convert back to BufferedImage
            return ImageUtils.toBufferedImage(gray);

        } catch (Exception e) {
            log.warn("OCR preprocessing failed, using original: {}", e.getMessage());
            return original;
        }
    }
    private String cleanOCRText(String text) {
        if (text == null) return "";

        return text.trim()
                .replaceAll("[\\r\\n]+", " ")  // Replace newlines with spaces
                .replaceAll("\\s+", " ")       // Collapse multiple spaces
                .replaceAll("[^a-zA-Z0-9\\s\\u0600-\\u06FF]", "") // Keep only alphanumeric, spaces, and Arabic
                .trim();
    }

    // Helper method to convert BufferedImage to Mat
    private Mat bufferedImageToMat(BufferedImage bi) {
        // Simple conversion - you might want to use more robust conversion
        return readMat(imageToByteArray(bi));
    }

    private byte[] imageToByteArray(BufferedImage bi) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert image to byte array", e);
        }
    }
}
