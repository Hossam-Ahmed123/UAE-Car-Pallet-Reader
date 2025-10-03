package com.example.uaecarpalletreader.config;

import com.example.uaecarpalletreader.service.detection.NoOpPlateDetector;
import com.example.uaecarpalletreader.service.detection.PlateDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a default {@link PlateDetector}. Projects that want to use a
 * specialised detector such as a YOLO model can replace this bean with their
 * own configuration.
 */
@Configuration
public class DetectorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DetectorConfiguration.class);

    @Bean
    public PlateDetector plateDetector() {
        log.info("Using fallback plate detector. Configure a YOLO-based implementation for higher accuracy.");
        return new NoOpPlateDetector();
    }
}

