package com.uae.anpr.util;

import java.util.HashMap;
import java.util.Map;

public final class EmirateParser {
    private static final Map<String, String> EMIRATES = new HashMap<>();

    static {
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
        EMIRATES.put("دبي", "Dubai");
        EMIRATES.put("ابوظبي", "Abu Dhabi");
        EMIRATES.put("أبوظبي", "Abu Dhabi");
        EMIRATES.put("الشارقة", "Sharjah");
        EMIRATES.put("عجمان", "Ajman");
        EMIRATES.put("رأس الخيمة", "Ras Al Khaimah");
        EMIRATES.put("الفجيرة", "Fujairah");
        EMIRATES.put("ام القيوين", "Umm Al Quwain");
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

    private EmirateParser() {
    }

    public static Parsed parse(String text) {
        Parsed parsed = new Parsed();
        if (text == null || text.isBlank()) {
            return parsed;
        }
        String cleaned = clean(text.toUpperCase());
        for (Map.Entry<String, String> entry : EMIRATES.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                parsed.emirate = entry.getValue();
                return parsed;
            }
        }
        for (Map.Entry<String, String> entry : EMIRATES.entrySet()) {
            if (entry.getKey().length() > 3 && containsPortion(cleaned, entry.getKey())) {
                parsed.emirate = entry.getValue();
                return parsed;
            }
        }
        return parsed;
    }

    private static String clean(String text) {
        return text.replaceAll("[\\\\\"'|\\[\\]{}]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean containsPortion(String text, String pattern) {
        int minLength = Math.max(3, pattern.length() - 2);
        for (int i = 0; i <= pattern.length() - minLength; i++) {
            int end = Math.min(pattern.length(), i + minLength);
            if (text.contains(pattern.substring(i, end))) {
                return true;
            }
        }
        return false;
    }

    public static Parsed parseWithConfidence(String text) {
        Parsed parsed = new Parsed();
        if (text == null || text.isBlank()) {
            return parsed;
        }
        String cleaned = clean(text.toUpperCase());
        Map<String, Integer> scores = new HashMap<>();
        for (Map.Entry<String, String> entry : EMIRATES.entrySet()) {
            int score = score(cleaned, entry.getKey());
            if (score > 0) {
                String emirate = entry.getValue();
                scores.put(emirate, Math.max(scores.getOrDefault(emirate, 0), score));
            }
        }
        if (!scores.isEmpty()) {
            parsed.emirate = scores.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        }
        return parsed;
    }

    private static int score(String text, String pattern) {
        int total = 0;
        if (text.contains(pattern)) {
            total += 10;
        }
        if (pattern.length() >= 4) {
            for (int len = pattern.length() - 1; len >= 3; len--) {
                for (int i = 0; i <= pattern.length() - len; i++) {
                    String part = pattern.substring(i, i + len);
                    if (text.contains(part)) {
                        total += len;
                    }
                }
            }
        }
        return total;
    }

    public static final class Parsed {
        public String number = "";
        public String letter = "";
        public String emirate = "Unknown";
    }
}
