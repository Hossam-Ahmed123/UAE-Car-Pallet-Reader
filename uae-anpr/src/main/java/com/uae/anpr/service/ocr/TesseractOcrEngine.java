package com.uae.anpr.service.ocr;

import com.uae.anpr.config.AnprProperties;
import java.io.File;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
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
        this(create(properties));
    }

    TesseractOcrEngine(ITesseract tesseract) {
        this.tesseract = Objects.requireNonNull(tesseract, "tesseract");
    }

    private static ITesseract create(AnprProperties properties) {
        Tesseract instance = new Tesseract();
        Path tessData = resolveTessData(properties);
        instance.setDatapath(tessData.toAbsolutePath().toString());
        instance.setLanguage(Optional.ofNullable(properties.ocr().language()).orElse("eng"));
        String whitelist = Optional.ofNullable(properties.ocr().whitelistPattern()).orElse("");
        if (properties.ocr().enableWhitelist()) {
            setVariable(instance, "tessedit_char_whitelist", whitelist);
        }
        setVariable(instance, "user_defined_dpi", "300");
        String numericMode = shouldForceNumericMode(whitelist) ? "1" : "0";
        setVariable(instance, "classify_bln_numeric_mode", numericMode);
        setVariable(instance, "preserve_interword_spaces", "1");
        return instance;
    }

    private static Path resolveTessData(AnprProperties properties) {
        String configuredPath = Optional.ofNullable(properties.ocr().datapath())
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .orElse(null);
        if (configuredPath == null) {
            return LoadLibs.extractTessResources("tessdata").toPath();
        }
        try {
            Path candidate = Path.of(configuredPath);
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return candidate;
            }
            log.warn(
                    "Configured Tesseract data path {} does not exist or is not a directory; falling back to bundled tessdata",
                    candidate);
        } catch (InvalidPathException ex) {
            log.warn(
                    "Configured Tesseract data path {} is invalid: {}. Falling back to bundled tessdata",
                    configuredPath,
                    ex.getMessage());
        }
        return LoadLibs.extractTessResources("tessdata").toPath();
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
            String normalized = text
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]", "")
                    .trim();
            if (normalized.isEmpty()) {
                return Optional.empty();
            }
            double confidence = readConfidence(temp);
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

    private double readConfidence(Path source) {
        try {
            File file = source.toFile();
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return 0.0;
            }
            List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            if (words == null || words.isEmpty()) {
                return 0.0;
            }
            OptionalDouble average = words.stream()
                    .mapToDouble(Word::getConfidence)
                    .filter(value -> value >= 0)
                    .average();
            if (average.isEmpty()) {
                return 0.0;
            }
            double scaled = average.getAsDouble() / 100.0;
            if (!Double.isFinite(scaled)) {
                return 0.0;
            }
            return Math.max(0.0, Math.min(1.0, scaled));
        } catch (IOException ex) {
            log.debug("Unable to read OCR source image for confidence: {}", ex.getMessage());
            return 0.0;
        } catch (RuntimeException ex) {
            log.debug("Tesseract confidence retrieval failed: {}", ex.getMessage());
            return 0.0;
        }
    }

    private byte[] encode(Mat candidate) {
        try (BytePointer buffer = new BytePointer()) {
            boolean encoded = opencv_imgcodecs.imencode(".png", candidate, buffer);
            if (!encoded) {
                throw new IllegalStateException("Failed to encode image as PNG");
            }
            byte[] bytes = new byte[(int) buffer.limit()];
            buffer.get(bytes);
            return bytes;
        }
    }

    private static void setVariable(Tesseract instance, String name, String value) {
        Method modernApi = resolveVariableMethod("setVariable");
        Method legacyApi = resolveVariableMethod("setTessVariable");
        Method target = modernApi != null ? modernApi : legacyApi;
        if (target == null) {
            throw new IllegalStateException("No supported API to set Tesseract variable " + name);
        }
        try {
            target.invoke(instance, name, value);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Unable to set Tesseract variable " + name, ex);
        }
    }

    private static Method resolveVariableMethod(String name) {
        try {
            return Tesseract.class.getMethod(name, String.class, String.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    public record OcrResult(String text, double confidence) {
    }

    static boolean shouldForceNumericMode(String whitelist) {
        if (whitelist == null) {
            return false;
        }
        String trimmed = whitelist.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.chars().noneMatch(Character::isLetter);
    }
}
