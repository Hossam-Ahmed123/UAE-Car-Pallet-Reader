package com.example.anpr.config;

import com.example.anpr.util.EmirateParser;
import jakarta.annotation.PreDestroy;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.microsoft.onnxruntime.OrtEnvironment;
import com.microsoft.onnxruntime.OrtException;
import com.microsoft.onnxruntime.OrtSession;

import java.nio.file.Path;

@Configuration
public class AnprConfig {

    private static final Logger log = LoggerFactory.getLogger(AnprConfig.class);

    private OrtEnvironment environment;
    private OrtSession session;
    private Tesseract tesseract;

    @Bean
    public OrtEnvironment ortEnvironment() {
        this.environment = OrtEnvironment.getEnvironment();
        return this.environment;
    }

    @Bean
    public OrtSession ortSession(OrtEnvironment environment, AnprProperties properties) throws OrtException {
        Path modelPath = Path.of(properties.getModelPath()).toAbsolutePath();
        log.info("Loading ONNX model from {}", modelPath);
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        this.session = environment.createSession(modelPath.toString(), options);
        return this.session;
    }

    @Bean
    public Tesseract tesseract(AnprProperties properties) throws TesseractException {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(Path.of(properties.getTessdataPath()).toAbsolutePath().toString());
        this.tesseract.setLanguage(properties.getOcrLang());
        this.tesseract.setTessVariable("user_defined_dpi", "300");
        this.tesseract.setTessVariable(
                "tessedit_char_whitelist",
                "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ دبيابوظبيالشارقةعجمانرأسالخيمةالفجيرةامالقيوين"
        );
        return this.tesseract;
    }

    @Bean
    public EmirateParser emirateParser() {
        return new EmirateParser();
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            log.warn("Failed to close OrtSession", e);
        }
        try {
            if (environment != null) {
                environment.close();
            }
        } catch (OrtException e) {
            log.warn("Failed to close OrtEnvironment", e);
        }
        if (tesseract != null) {
            tesseract.close();
        }
    }
}
