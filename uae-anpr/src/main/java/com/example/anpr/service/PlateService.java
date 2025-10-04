package com.example.anpr.service;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.dto.PlateResult;
import com.example.anpr.util.EmirateParser;
import com.example.anpr.util.ImageUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlateService {

    private final OcrService ocr;

    public PlateService(OcrService ocr) {
        this.ocr = ocr;
    }

    public PlateResponse recognize(byte[] imageBytes) {
        Mat src = ImageUtils.readMat(imageBytes);

        Rect roi = ImageUtils.tryFindPlateROI(src);
        Mat plate = (roi != null) ? new Mat(src, roi).clone() : src;

        plate = ImageUtils.resize(plate, 2.0);

        int W = plate.cols(), H = plate.rows();
        int leftW = Math.max(30, (int)(W * 0.32));
        Rect leftRect = new Rect(0, 0, leftW, H);
        Rect rightRect = new Rect(leftW, 0, W - leftW, H);

        Mat left = new Mat(plate, leftRect).clone();
        Mat right = new Mat(plate, rightRect).clone();

        Mat leftGray = ImageUtils.toGray(left);
        Mat leftBin = ImageUtils.adaptive(leftGray);
        Mat leftInv = new Mat();
        opencv_core.bitwise_not(leftBin, leftInv);
        Mat leftThick = new Mat();
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new org.bytedeco.opencv.opencv_core.Size(2,2));
        opencv_imgproc.dilate(leftBin, leftThick, kernel);
        Mat leftThin = new Mat();
        opencv_imgproc.erode(leftBin, leftThin, kernel);

        Mat rightGray = ImageUtils.toGray(right);
        Mat rightBin = ImageUtils.enhanceForOCR(rightGray);

        String digitsRaw  = ocr.ocrDigits(ImageUtils.toBufferedImage(rightBin));

        String emirA = ocr.ocrEmirate(ImageUtils.toBufferedImage(leftBin));
        String emirB = ocr.ocrEmirate(ImageUtils.toBufferedImage(leftInv));
        String emirC = ocr.ocrEmirate(ImageUtils.toBufferedImage(leftThick));
        String emirateRaw = String.join(" | ", Arrays.asList(emirA, emirB, emirC));

        String L1 = normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(leftBin)));
        String L2 = normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(leftInv)));
        String L3 = normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(leftThick)));
        String L4 = normalizeLetter(ocr.ocrLetters(ImageUtils.toBufferedImage(leftThin)));
        String letter = majorityLetter(L1, L2, L3, L4);

        EmirateParser.Parsed parsed = EmirateParser.parse(emirateRaw + " " + digitsRaw + " " + letter);
        if ("Unknown".equals(parsed.emirate)) {
            String t = (emirateRaw + " " + digitsRaw).toUpperCase();
            if (t.contains("DUBAI") || t.contains("دبي")) parsed.emirate = "Dubai";
            if (t.contains("ABU DHABI") || t.contains("ابوظبي") || t.contains("أبوظبي")) parsed.emirate = "Abu Dhabi";
        }

        String number = digitsRaw.replaceAll("\\D+", "");
        parsed.number = number.isBlank() ? parsed.number : number;
        parsed.letter = (letter == null || letter.isBlank()) ? parsed.letter : letter;

        PlateResult one = new PlateResult(parsed.number, parsed.letter, parsed.emirate,
                "EMIRATE_RAW=" + emirateRaw.replace('\n',' ').trim()
                + " | DIGITS_RAW=" + digitsRaw.trim()
                + " | LETTERS_TRIED=" + String.join(",", Arrays.asList(L1,L2,L3,L4)));
        return PlateResponse.of(one);
    }

    private static String normalizeLetter(String s) {
        if (s == null) return "";
        s = s.replaceAll("[^A-Z]","").toUpperCase();
        if (s.length() > 2) s = s.substring(0,2);
        return s;
    }

    private static String majorityLetter(String... options) {
        Map<String,Integer> cnt = new HashMap<>();
        for (String o: options) {
            if (o==null || o.isBlank()) continue;
            cnt.put(o, cnt.getOrDefault(o,0)+1);
        }
        if (cnt.isEmpty()) return "";
        return cnt.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
    }
}
