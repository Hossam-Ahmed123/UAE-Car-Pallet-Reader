package com.example.uaecarpalletreader.config;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration
public class TesseractConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TesseractConfiguration.class);

    @Value("${tesseract.datapath:}")
    private String dataPath;

    @Value("${tesseract.language:eng}")
    private String language;

    @Bean
    public Tesseract tesseract() throws TesseractException {
        Tesseract tesseract = new Tesseract();
        String resolvedDataPath = resolveDataPath();
        if (resolvedDataPath != null) {
            log.info("Configuring Tesseract data path: {}", resolvedDataPath);
            tesseract.setDatapath(resolvedDataPath);
        } else {
            String message = String.format(Locale.ROOT,
                    "Unable to locate Tesseract language data for '%s'. " +
                            "Provide it via the tesseract.datapath property or the TESSDATA_PREFIX environment variable.",
                    language);
            log.error(message);
            throw new IllegalStateException(message);
        }
        tesseract.setLanguage(language);
        tesseract.setOcrEngineMode(1); // LSTM only
        tesseract.setPageSegMode(6); // Assume a block of text
        return tesseract;
    }

    private String resolveDataPath() {
        List<String> candidates = new ArrayList<>();
        if (dataPath != null && !dataPath.isBlank()) {
            candidates.add(dataPath);
        }
        String envCandidate = System.getenv("TESSDATA_PREFIX");
        if (envCandidate != null && !envCandidate.isBlank()) {
            candidates.add(envCandidate);
        }
        String systemPropertyCandidate = System.getProperty("TESSDATA_PREFIX");
        if (systemPropertyCandidate != null && !systemPropertyCandidate.isBlank()) {
            candidates.add(systemPropertyCandidate);
        }

        candidates.add("/usr/share/tesseract-ocr/5/tessdata");
        candidates.add("/usr/share/tesseract-ocr/4.00/tessdata");
        candidates.add("C:/Program Files/Tesseract-OCR/tessdata");

        for (String candidate : candidates) {
            Path validPath = validateCandidate(candidate);
            if (validPath != null) {
                return validPath.toString();
            }
        }
        return null;
    }

    private Path validateCandidate(String candidate) {
        Path basePath = Paths.get(candidate).normalize();
        if (!Files.isDirectory(basePath)) {
            return null;
        }

        Path directLanguageFile = basePath.resolve(language + ".traineddata");
        if (Files.isRegularFile(directLanguageFile)) {
            return basePath;
        }

        Path tessdataDirectory = basePath.resolve("tessdata");
        if (Files.isDirectory(tessdataDirectory)) {
            Path nestedLanguageFile = tessdataDirectory.resolve(language + ".traineddata");
            if (Files.isRegularFile(nestedLanguageFile)) {
                return tessdataDirectory;
            }
        }

        log.debug("Tesseract data path candidate '{}' does not contain {}.traineddata", candidate, language);
        return null;
    }
}
