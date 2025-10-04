package com.example.anpr.util;

import com.example.anpr.dto.PlateResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmirateParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d{3,6})\\b");
    private static final Pattern LETTER_PATTERN = Pattern.compile("\\b([A-Z]{1,2})\\b");

    private static final Map<String, String> EMIRATE_KEYWORDS = new LinkedHashMap<>() {{
        put("dubai", "Dubai");
        put("دبي", "Dubai");
        put("abu dhabi", "Abu Dhabi");
        put("ابوظبي", "Abu Dhabi");
        put("abu dhaby", "Abu Dhabi");
        put("sharjah", "Sharjah");
        put("الشارقة", "Sharjah");
        put("ajman", "Ajman");
        put("عجمان", "Ajman");
        put("ras al khaimah", "Ras Al Khaimah");
        put("رأس الخيمة", "Ras Al Khaimah");
        put("umm al quwain", "Umm Al Quwain");
        put("ام القيوين", "Umm Al Quwain");
        put("fujairah", "Fujairah");
        put("الفجيرة", "Fujairah");
    }};

    public ParsedData parse(String rawText) {
        if (rawText == null) {
            return new ParsedData(null, null, null);
        }
        String normalized = rawText.replaceAll("[\n\r]+", " ").trim();
        String upper = normalized.toUpperCase();

        String number = extract(NUMBER_PATTERN, normalized);
        String letter = Optional.ofNullable(extract(LETTER_PATTERN, upper)).map(String::toUpperCase).orElse(null);
        String emirate = detectEmirate(normalized.toLowerCase());
        return new ParsedData(number, letter, emirate);
    }

    private String detectEmirate(String text) {
        for (Map.Entry<String, String> entry : EMIRATE_KEYWORDS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void apply(PlateResult result, String rawText) {
        ParsedData parsed = parse(rawText);
        if (result.getNumber() == null) {
            result.setNumber(parsed.number());
        }
        if (result.getLetter() == null) {
            result.setLetter(parsed.letter());
        }
        if (result.getEmirate() == null) {
            result.setEmirate(parsed.emirate());
        }
    }

    public record ParsedData(String number, String letter, String emirate) {
    }
}
