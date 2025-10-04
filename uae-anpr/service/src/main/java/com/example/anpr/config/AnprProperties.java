package com.example.anpr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anpr")
public class AnprProperties {

    private String modelPath;
    private int imgsz;
    private double confThreshold;
    private double iouThreshold;
    private String tessdataPath;
    private String ocrLang;
    private boolean returnRawText;

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public int getImgsz() {
        return imgsz;
    }

    public void setImgsz(int imgsz) {
        this.imgsz = imgsz;
    }

    public double getConfThreshold() {
        return confThreshold;
    }

    public void setConfThreshold(double confThreshold) {
        this.confThreshold = confThreshold;
    }

    public double getIouThreshold() {
        return iouThreshold;
    }

    public void setIouThreshold(double iouThreshold) {
        this.iouThreshold = iouThreshold;
    }

    public String getTessdataPath() {
        return tessdataPath;
    }

    public void setTessdataPath(String tessdataPath) {
        this.tessdataPath = tessdataPath;
    }

    public String getOcrLang() {
        return ocrLang;
    }

    public void setOcrLang(String ocrLang) {
        this.ocrLang = ocrLang;
    }

    public boolean isReturnRawText() {
        return returnRawText;
    }

    public void setReturnRawText(boolean returnRawText) {
        this.returnRawText = returnRawText;
    }
}
