package com.example.uaecarpalletreader.config;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        if (dataPath != null && !dataPath.isBlank()) {
            log.info("Configuring Tesseract data path: {}", dataPath);
            tesseract.setDatapath(dataPath);
        } else {
            log.warn("No explicit tesseract.datapath configured. Using default search paths.");
        }
        tesseract.setLanguage(language);
        tesseract.setOcrEngineMode(1); // LSTM only
        tesseract.setPageSegMode(6); // Assume a block of text
        return tesseract;
    }
}
