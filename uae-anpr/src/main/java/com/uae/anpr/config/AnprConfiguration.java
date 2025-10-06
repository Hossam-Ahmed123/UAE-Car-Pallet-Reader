package com.uae.anpr.config;

import net.sf.javaanpr.intelligence.Intelligence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

@Configuration
public class AnprConfiguration {
    @Bean
    public Intelligence intelligence() {
        try {
            return new Intelligence();
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalStateException("Unable to initialize recognition engine", exception);
        }
    }
}
