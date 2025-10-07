package com.uae.anpr.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anpr")
public record AnprProperties(
        ResourceSet resources,
        OcrProperties ocr) {

    public record ResourceSet(
            String alphabets,
            String neuralNetworks,
            String configs,
            List<String> enhancementKernels) {
    }

    public record OcrProperties(
            String language,
            double confidenceThreshold,
            boolean enableWhitelist,
            String whitelistPattern) {
    }
}
