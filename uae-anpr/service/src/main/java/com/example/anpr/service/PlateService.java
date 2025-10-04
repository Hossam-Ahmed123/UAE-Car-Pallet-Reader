package com.example.anpr.service;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.dto.PlateResult;
import com.example.anpr.exception.PlateNotFoundException;
import com.example.anpr.exception.PlateProcessingException;
import com.example.anpr.util.EmirateParser;
import com.example.anpr.util.ImageUtils;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlateService {

    private static final Logger log = LoggerFactory.getLogger(PlateService.class);

    private final YoloOnnxService yoloOnnxService;
    private final OcrService ocrService;
    private final EmirateParser emirateParser;

    public PlateService(YoloOnnxService yoloOnnxService, OcrService ocrService, EmirateParser emirateParser) {
        this.yoloOnnxService = yoloOnnxService;
        this.ocrService = ocrService;
        this.emirateParser = emirateParser;
    }

    public PlateResponse recognize(byte[] imageBytes) {
        long start = System.nanoTime();
        Mat image = ImageUtils.readImage(imageBytes);
        if (image == null || image.empty()) {
            if (image != null) {
                image.close();
            }
            throw new PlateProcessingException("Unable to decode input image", null);
        }

        List<PlateResult> results = new ArrayList<>();
        try {
            List<YoloOnnxService.Detection> detections = yoloOnnxService.detect(image);
            if (detections.isEmpty()) {
                throw new PlateNotFoundException("No licence plates detected");
            }
            for (YoloOnnxService.Detection detection : detections) {
                Rect rect = detection.toRect();
                Mat roi = new Mat(image, rect).clone();
                try {
                    OcrService.OcrResult ocrResult = ocrService.recognize(roi);
                    PlateResult plateResult = new PlateResult();
                    plateResult.setConfidence(detection.confidence());
                    plateResult.setX(rect.x());
                    plateResult.setY(rect.y());
                    plateResult.setWidth(rect.width());
                    plateResult.setHeight(rect.height());
                    plateResult.setRawText(ocrResult.raw() != null ? ocrResult.raw() : ocrResult.cleaned());
                    emirateParser.apply(plateResult, ocrResult.cleaned());
                    results.add(plateResult);
                } catch (TesseractException e) {
                    throw new PlateProcessingException("OCR failed", e);
                } finally {
                    roi.close();
                }
            }
        } catch (PlateNotFoundException e) {
            image.close();
            throw e;
        } catch (Exception e) {
            image.close();
            if (e instanceof PlateProcessingException) {
                throw (PlateProcessingException) e;
            }
            throw new PlateProcessingException("Detection failed", e);
        }
        image.close();
        long end = System.nanoTime();
        log.info("Processed image with {} detections in {} ms", results.size(), (end - start) / 1_000_000.0);
        return new PlateResponse(results);
    }
}
