package com.uae.anpr.service.ocr;

import com.uae.anpr.config.AnprProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.LoadLibs;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TesseractOcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    private final ITesseract tesseract;

    public TesseractOcrEngine(AnprProperties properties) {
        this.tesseract = create(properties);
    }

    private ITesseract create(AnprProperties properties) {
        Tesseract instance = new Tesseract();
        Path tessData = LoadLibs.extractTessResources("tessdata").toPath();
        instance.setDatapath(tessData.toAbsolutePath().toString());
        instance.setLanguage(Optional.ofNullable(properties.ocr().language()).orElse("eng"));
        if (properties.ocr().enableWhitelist()) {
            instance.setVariable("tessedit_char_whitelist", properties.ocr().whitelistPattern());
        }
        instance.setVariable("user_defined_dpi", "300");
        instance.setVariable("classify_bln_numeric_mode", "1");
        instance.setVariable("preserve_interword_spaces", "1");
        return instance;
    }

    public Optional<OcrResult> recognize(Mat candidate) {
        Path temp = null;
        try {
            byte[] encoded = encode(candidate);
            temp = Files.createTempFile("candidate-", ".png");
            Files.write(temp, encoded);
            String text = tesseract.doOCR(temp.toFile());
            if (text == null) {
                return Optional.empty();
            }
            String normalized = text.replaceAll("[^A-Z0-9]", "").trim();
            if (normalized.isEmpty()) {
                return Optional.empty();
            }
            double confidence = extractConfidence(text);
            log.debug("OCR recognized {} with confidence {}", normalized, confidence);
            return Optional.of(new OcrResult(normalized, confidence));
        } catch (IOException ex) {
            log.error("Failed to persist candidate for OCR: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Tesseract failed: {}", ex.getMessage());
            return Optional.empty();
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignore) {
                    log.debug("Unable to delete temporary OCR file {}", temp);
                }
            }
        }
    }

    private double extractConfidence(String text) {
        // Tess4J does not provide fine grained confidence without ResultIterator; placeholder at 0.95.
        return 0.95;
    }

    private byte[] encode(Mat candidate) {
        BytePointer buffer = new BytePointer();
        opencv_imgcodecs.imencode(".png", candidate, buffer);
        byte[] bytes = new byte[(int) buffer.limit()];
        buffer.get(bytes);
        buffer.deallocate();
        return bytes;
    }

    public record OcrResult(String text, double confidence) {
    }
}
