package com.example.uaecarpalletreader.service;

import com.example.uaecarpalletreader.model.BoundingBox;
import com.example.uaecarpalletreader.model.PlateExtractionResult;
import com.example.uaecarpalletreader.service.detection.PlateDetector;
import com.example.uaecarpalletreader.util.ImagePreprocessor;
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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PlateRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(PlateRecognitionService.class);

    private final Tesseract tesseract;
    private final String language;
    private final PlateDetector plateDetector;

    public PlateRecognitionService(Tesseract tesseract,
                                   @Value("${tesseract.language:eng}") String language,
                                   PlateDetector plateDetector) {
        this.tesseract = tesseract;
        this.language = language;
        this.plateDetector = plateDetector;
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

            PlateCandidate bestCandidate = evaluateCandidates(bufferedImage);
            if (bestCandidate == null) {
                throw new IllegalStateException("Unable to extract plate information from " + fileName);
            }

            NormalizedPlate normalized = bestCandidate.normalized();
            return new PlateExtractionResult(
                    fileName,
                    bestCandidate.rawText(),
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

    private PlateCandidate evaluateCandidates(BufferedImage image) throws TesseractException {
        List<BoundingBox> boxes = new ArrayList<>(plateDetector.detect(image));
        if (boxes.isEmpty()) {
            boxes.add(new BoundingBox(0, 0, image.getWidth(), image.getHeight()));
        }

        List<PlateCandidate> candidates = new ArrayList<>();
        for (BoundingBox box : boxes) {
            BufferedImage cropped = crop(image, box);
            if (cropped.getWidth() < 20 || cropped.getHeight() < 20) {
                log.debug("Skipping tiny candidate {}", box);
                continue;
            }
            BufferedImage preprocessed = ImagePreprocessor.preprocess(cropped);
            String rawText = safeOcr(preprocessed);
            if (rawText == null || rawText.isBlank()) {
                log.debug("OCR returned empty result for candidate {}", box);
                continue;
            }
            NormalizedPlate normalized = PlateNumberNormalizer.normalize(rawText);
            candidates.add(new PlateCandidate(rawText.trim(), normalized, box));
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .max(Comparator.comparingInt(this::scoreCandidate))
                .orElse(null);
    }

    private BufferedImage crop(BufferedImage source, BoundingBox box) {
        int x = clamp(box.x(), 0, source.getWidth() - 1);
        int y = clamp(box.y(), 0, source.getHeight() - 1);
        int width = clamp(box.width(), 1, source.getWidth() - x);
        int height = clamp(box.height(), 1, source.getHeight() - y);
        return source.getSubimage(x, y, width, height);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String safeOcr(BufferedImage image) throws TesseractException {
        String raw = tesseract.doOCR(image);
        return raw != null ? raw.replace('\u0000', ' ').trim() : null;
    }

    private int scoreCandidate(PlateCandidate candidate) {
        NormalizedPlate normalized = candidate.normalized();
        int score = 0;
        if (normalized.normalizedPlate() != null) {
            score += normalized.normalizedPlate().replace(" ", "").length() * 2;
        }
        if (normalized.number() != null) {
            score += normalized.number().length() * 3;
        }
        if (normalized.letters() != null) {
            score += normalized.letters().replace(" ", "").length() * 2;
        }
        if (normalized.city() != null) {
            score += 2;
        }

        long digitCount = candidate.rawText().chars().filter(Character::isDigit).count();
        long letterCount = candidate.rawText().chars().filter(Character::isLetter).count();
        score += (int) (digitCount + letterCount);
        return score;
    }

    private record PlateCandidate(String rawText, NormalizedPlate normalized, BoundingBox boundingBox) {
        PlateCandidate {
            Objects.requireNonNull(normalized, "normalized");
        }
    }
}
