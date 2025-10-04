package com.example.anpr.service;

import com.example.anpr.dto.PlateResponse;
import com.example.anpr.dto.PlateResult;
import com.example.anpr.util.EmirateParser;
import com.example.anpr.util.ImageUtils;
import org.bytedeco.javacpp.indexer.UByteRawIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class PlateService {

    private final OcrService ocr;

    public PlateService(OcrService ocr) {
        this.ocr = ocr;
    }

    public PlateResponse recognize(byte[] imageBytes) {
        Mat src = ImageUtils.readMat(imageBytes);

        PlateResult best = recognizeFrom(src, true, "ORIGINAL");
        best = better(best, recognizeFrom(src, false, "ORIGINAL"));

        if (confidence(best) < 4 && Math.max(src.cols(), src.rows()) < 1200) {
            PlateResult scaled = recognizeFrom(ImageUtils.resize(src, 1.5), true, "UPSCALED");
            best = better(best, scaled);
        }

        return PlateResponse.of(best);
    }

    private PlateResult recognizeFrom(Mat src, boolean tryRoi, String stageLabel) {
        Rect roi = tryRoi ? ImageUtils.tryFindPlateROI(src) : null;
        Mat plate = (roi != null) ? new Mat(src, roi).clone() : src.clone();
        Mat normalized = ImageUtils.ensureHeight(plate, 240, 720);
        String stage = stageLabel + (roi != null ? "_CROP" : "_FULL");
        return analyzePlate(normalized, roi, stage);
    }

    private PlateResult analyzePlate(Mat plate, Rect roi, String stage) {
        Mat plateGray = ImageUtils.toGray(plate);
        int W = plate.cols();
        int H = plate.rows();

        int estimatedSplit = estimateLeftBandWidth(plateGray);
        int minLeft = Math.max(20, (int) (W * 0.18));
        int maxLeft = Math.max(minLeft + 1, W - 40);
        int leftW = Math.max(minLeft, Math.min(estimatedSplit, maxLeft));
        if (W - leftW < 40) {
            leftW = Math.max(1, W - 40);
        }
        leftW = Math.min(leftW, W - 1);
        Rect leftRect = new Rect(0, 0, leftW, H);
        Rect rightRect = new Rect(leftW, 0, W - leftW, H);

        Mat left = new Mat(plate, leftRect).clone();
        Mat right = new Mat(plate, rightRect).clone();

        Mat leftGray = ImageUtils.toGray(left);
        Mat leftBin = ImageUtils.adaptive(leftGray);
        Mat leftInv = new Mat();
        opencv_core.bitwise_not(leftBin, leftInv);
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT,
                new org.bytedeco.opencv.opencv_core.Size(2, 2));
        Mat leftThick = new Mat();
        opencv_imgproc.dilate(leftBin, leftThick, kernel);
        Mat leftThin = new Mat();
        opencv_imgproc.erode(leftBin, leftThin, kernel);

        Mat rightGray = ImageUtils.toGray(right);
        Mat rightBin = ImageUtils.enhanceForOCR(rightGray);
        Mat rightClose = new Mat();
        opencv_imgproc.morphologyEx(rightBin, rightClose, opencv_imgproc.MORPH_CLOSE,
                opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT,
                        new org.bytedeco.opencv.opencv_core.Size(3, 3)));

        String digitsA = ocr.ocrDigits(ImageUtils.toBufferedImage(rightBin));
        String digitsB = ocr.ocrDigits(ImageUtils.toBufferedImage(rightClose));
        String digitsRaw = bestDigits(digitsA, digitsB);

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
            if (t.contains("SHARJAH") || t.contains("الشارقة")) parsed.emirate = "Sharjah";
            if (t.contains("AJMAN") || t.contains("عجمان")) parsed.emirate = "Ajman";
        }

        String number = digitsRaw.replaceAll("\\D+", "");
        parsed.number = number.isBlank() ? parsed.number : number;
        parsed.letter = (letter == null || letter.isBlank()) ? parsed.letter : letter;

        String diagnostics = "STAGE=" + stage
                + (roi != null ? " ROI=" + roi.width() + "x" + roi.height() : " ROI=NONE")
                + " | CUT=" + leftW + "/" + W
                + " | EMIRATE_RAW=" + emirateRaw.replace('\n', ' ').trim()
                + " | DIGITS_RAW=" + digitsRaw.trim()
                + " | LETTERS_TRIED=" + String.join(",", Arrays.asList(L1, L2, L3, L4));

        return new PlateResult(parsed.number, parsed.letter, parsed.emirate, diagnostics);
    }

    private static int estimateLeftBandWidth(Mat plateGray) {
        Mat blur = new Mat();
        opencv_imgproc.GaussianBlur(plateGray, blur,
                new org.bytedeco.opencv.opencv_core.Size(3, 3), 0);
        Mat gradX = new Mat();
        opencv_imgproc.Sobel(blur, gradX, opencv_core.CV_16S, 1, 0, 3, 1, 0, opencv_core.BORDER_DEFAULT);
        Mat absGrad = new Mat();
        opencv_core.convertScaleAbs(gradX, absGrad);

        int width = absGrad.cols();
        int height = absGrad.rows();
        int min = Math.max(10, (int) (width * 0.18));
        int max = Math.min(width - 10, (int) (width * 0.65));
        if (max <= min) {
            return Math.max(10, Math.min((int) (width * 0.32), width - 10));
        }

        int best = (int) (width * 0.32);
        double bestScore = -1;
        try (UByteRawIndexer indexer = absGrad.createIndexer()) {
            for (int x = min; x < max; x++) {
                double score = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    int col = Math.max(min, Math.min(max - 1, x + dx));
                    for (int y = 0; y < height; y++) {
                        score += indexer.get(y, col) & 0xFF;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = x;
                }
            }
        }
        int defaultCut = Math.max(10, Math.min((int) (width * 0.32), width - 10));
        int candidate = Math.max(10, Math.min(best, width - 10));
        if (candidate <= 10 || candidate >= width - 10) {
            candidate = defaultCut;
        }
        return Math.max(10, Math.min(candidate, width - 10));
    }

    private PlateResult better(PlateResult current, PlateResult candidate) {
        if (confidence(candidate) > confidence(current)) {
            return candidate;
        }
        if (confidence(candidate) == confidence(current)
                && digitScore(candidate != null ? candidate.number() : "")
                > digitScore(current != null ? current.number() : "")) {
            return candidate;
        }
        return current;
    }

    private static int confidence(PlateResult result) {
        if (result == null) {
            return -1;
        }
        int score = 0;
        if (result.number() != null && !result.number().isBlank()) {
            score += Math.min(5, result.number().replaceAll("\\D+", "").length());
        }
        if (result.letter() != null && !result.letter().isBlank()) {
            score += 2;
        }
        if (result.emirate() != null && !result.emirate().isBlank()
                && !"Unknown".equalsIgnoreCase(result.emirate())) {
            score += 2;
        }
        return score;
    }

    private static String bestDigits(String... options) {
        return Arrays.stream(options)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(PlateService::digitScore))
                .orElse("");
    }

    private static int digitScore(String value) {
        if (value == null) {
            return -1;
        }
        return value.replaceAll("\\D+", "").length();
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
