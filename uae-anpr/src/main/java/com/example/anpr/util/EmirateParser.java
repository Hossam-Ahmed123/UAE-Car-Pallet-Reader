package com.example.anpr.util;

import java.util.Map;

public class EmirateParser {
    private static final Map<String, String> EMIRATES = Map.ofEntries(
            Map.entry("DUBAI", "Dubai"),
            Map.entry("دبي", "Dubai"),
            Map.entry("ABU DHABI", "Abu Dhabi"),
            Map.entry("ابوظبي", "Abu Dhabi"),
            Map.entry("أبوظبي", "Abu Dhabi"),
            Map.entry("SHARJAH", "Sharjah"),
            Map.entry("الشارقة", "Sharjah"),
            Map.entry("AJMAN", "Ajman"),
            Map.entry("عجمان", "Ajman"),
            Map.entry("RAK", "Ras Al Khaimah"),
            Map.entry("رأس الخيمة", "Ras Al Khaimah"),
            Map.entry("FUJAIRAH", "Fujairah"),
            Map.entry("الفجيرة", "Fujairah"),
            Map.entry("UMM AL QUWAIN", "Umm Al Quwain"),
            Map.entry("ام القيوين", "Umm Al Quwain")
    );

    public static class Parsed {
        public String number="", letter="", emirate="Unknown";
    }

    public static Parsed parse(String text) {
        Parsed p = new Parsed();
        String upper = text.toUpperCase();
        for (var entry : EMIRATES.entrySet()) {
            if (upper.contains(entry.getKey().toUpperCase())) {
                p.emirate = entry.getValue();
                break;
            }
        }
        return p;
    }
}
