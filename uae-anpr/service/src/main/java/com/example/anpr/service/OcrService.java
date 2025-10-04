package com.example.anpr.service;

import com.example.anpr.config.AnprProperties;
import com.example.anpr.util.ImageUtils;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final Tesseract tesseract;
    private final AnprProperties properties;

    public OcrService(Tesseract tesseract, AnprProperties properties) {
        this.tesseract = tesseract;
        this.properties = properties;
    }

    public synchronized OcrResult recognize(Mat plateRoi) throws TesseractException {
        long start = System.nanoTime();
        Mat gray = new Mat();
        if (plateRoi.channels() == 3) {
            opencv_imgproc.cvtColor(plateRoi, gray, opencv_imgproc.COLOR_BGR2GRAY);
        } else {
            plateRoi.copyTo(gray);
        }
        Mat denoised = new Mat();
        opencv_imgproc.bilateralFilter(gray, denoised, 5, 75, 75);
        Mat thresh = new Mat();
        opencv_imgproc.threshold(denoised, thresh, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);
        BufferedImage buffered = ImageUtils.matToBufferedImage(thresh);
        gray.close();
        denoised.close();
        thresh.close();

        String raw = tesseract.doOCR(buffered);
        String cleaned = postProcessText(raw);
        long end = System.nanoTime();
        log.debug("OCR time: {} ms", (end - start) / 1_000_000.0);
        return new OcrResult(cleaned, properties.isReturnRawText() ? raw : null);
    }

    public static String postProcessText(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("[^0-9A-Za-zدبيابوظبيالشارقةعجمانرأسالخيمةالفجيرةامالقيوين]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();
    }

    public record OcrResult(String cleaned, String raw) {
    }
}
