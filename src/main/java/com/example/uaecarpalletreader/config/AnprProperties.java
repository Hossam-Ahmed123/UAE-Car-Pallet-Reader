package com.example.uaecarpalletreader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anpr")
public class AnprProperties {

    private boolean enabled = true;
    private String modelPath = "./models/yolov8n_plate.onnx";
    private int inputSize = 640;
    private double confThreshold = 0.25;
    private double nmsThreshold = 0.45;
    private String tessdataPath = "C:/Program Files/Tesseract-OCR/tessdata";
    private String languages = "ara+eng";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public int getInputSize() {
        return inputSize;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    public double getConfThreshold() {
        return confThreshold;
    }

    public void setConfThreshold(double confThreshold) {
        this.confThreshold = confThreshold;
    }

    public double getNmsThreshold() {
        return nmsThreshold;
    }

    public void setNmsThreshold(double nmsThreshold) {
        this.nmsThreshold = nmsThreshold;
    }

    public String getTessdataPath() {
        return tessdataPath;
    }

    public void setTessdataPath(String tessdataPath) {
        this.tessdataPath = tessdataPath;
    }

    public String getLanguages() {
        return languages;
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }
}
