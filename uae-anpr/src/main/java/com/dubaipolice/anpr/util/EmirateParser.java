package com.dubaipolice.anpr.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class EmirateParser {
    private static final Map<String, String> EMIRATES = new HashMap<>();

    static {
        // English names
        EMIRATES.put("DUBAI", "Dubai");
        EMIRATES.put("DXB", "Dubai");
        EMIRATES.put("ABU DHABI", "Abu Dhabi");
        EMIRATES.put("ABUDHABI", "Abu Dhabi");
        EMIRATES.put("AUH", "Abu Dhabi");
        EMIRATES.put("SHARJAH", "Sharjah");
        EMIRATES.put("SHJ", "Sharjah");
        EMIRATES.put("AJMAN", "Ajman");
        EMIRATES.put("AJMN", "Ajman");
        EMIRATES.put("RAK", "Ras Al Khaimah");
        EMIRATES.put("RAS AL KHAIMAH", "Ras Al Khaimah");
        EMIRATES.put("FUJAIRAH", "Fujairah");
        EMIRATES.put("FJR", "Fujairah");
        EMIRATES.put("UMM AL QUWAIN", "Umm Al Quwain");
        EMIRATES.put("UMM ALQUWAIN", "Umm Al Quwain");
        EMIRATES.put("UAQ", "Umm Al Quwain");

        // Arabic names
        EMIRATES.put("دبي", "Dubai");
        EMIRATES.put("ابوظبي", "Abu Dhabi");
        EMIRATES.put("أبوظبي", "Abu Dhabi");
        EMIRATES.put("الشارقة", "Sharjah");
        EMIRATES.put("عجمان", "Ajman");
        EMIRATES.put("رأس الخيمة", "Ras Al Khaimah");
        EMIRATES.put("الفجيرة", "Fujairah");
        EMIRATES.put("ام القيوين", "Umm Al Quwain");

        // Common OCR misreadings
        EMIRATES.put("DUBRI", "Dubai");
        EMIRATES.put("DUBHI", "Dubai");
        EMIRATES.put("DUBA", "Dubai");
        EMIRATES.put("ABU", "Abu Dhabi");
        EMIRATES.put("SHAR", "Sharjah");
        EMIRATES.put("SHARIAH", "Sharjah");
        EMIRATES.put("SHARIAN", "Sharjah");
        EMIRATES.put("AJMA", "Ajman");
        EMIRATES.put("RAS", "Ras Al Khaimah");
        EMIRATES.put("FUJ", "Fujairah");
        EMIRATES.put("UMM", "Umm Al Quwain");
    }

    public static class Parsed {
        public String number = "";
        public String letter = "";
        public String emirate = "Unknown";
    }

    public static Parsed parse(String text) {
        Parsed p = new Parsed();
        if (text == null || text.trim().isEmpty()) {
            return p;
        }

        String upper = text.toUpperCase().trim();

        // Remove common OCR noise
        upper = cleanText(upper);

        // Try exact matches first
        for (Map.Entry<String, String> entry : EMIRATES.entrySet()) {
            if (upper.contains(entry.getKey())) {
                p.emirate = entry.getValue();
                return p;
            }
        }

        // Try partial matches for common OCR errors
        for (Map.Entry<String, String> entry : EMIRATES.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 3) {
                // Check if the text contains a significant portion of the emirate name
                if (containsPartialMatch(upper, key)) {
                    p.emirate = entry.getValue();
                    return p;
                }
            }
        }

        return p;
    }

    private static String cleanText(String text) {
        // Remove common OCR artifacts and noise
        return text.replaceAll("[\"\'\\|\\[\\]{}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsPartialMatch(String text, String pattern) {
        if (text.length() < 3) return false;

        // Check for significant substring matches
        int minLength = Math.max(3, pattern.length() - 2);
        for (int i = 0; i <= pattern.length() - minLength; i++) {
            String substring = pattern.substring(i, Math.min(i + minLength, pattern.length()));
            if (text.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    // Enhanced detection with confidence scoring
    public static Parsed parseWithConfidence(String text) {
        Parsed p = new Parsed();
        if (text == null || text.trim().isEmpty()) {
            return p;
        }

        String upper = text.toUpperCase().trim();
        upper = cleanText(upper);

        Map<String, Integer> scores = new HashMap<>();

        for (Map.Entry<String, String> entry : EMIRATES.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            int score = calculateMatchScore(upper, key);
            if (score > 0) {
                scores.put(value, Math.max(scores.getOrDefault(value, 0), score));
            }
        }

        if (!scores.isEmpty()) {
            p.emirate = scores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .get()
                    .getKey();
        }

        return p;
    }

    private static int calculateMatchScore(String text, String pattern) {
        int score = 0;

        // Exact match
        if (text.contains(pattern)) {
            score += 10;
        }

        // Partial matches
        if (pattern.length() >= 4) {
            for (int len = pattern.length() - 1; len >= 3; len--) {
                for (int i = 0; i <= pattern.length() - len; i++) {
                    String substring = pattern.substring(i, i + len);
                    if (text.contains(substring)) {
                        score += len; // Longer matches score higher
                    }
                }
            }
        }

        return score;
    }
}