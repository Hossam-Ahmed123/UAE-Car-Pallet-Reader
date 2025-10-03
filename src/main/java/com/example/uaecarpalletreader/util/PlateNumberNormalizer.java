package com.example.uaecarpalletreader.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlateNumberNormalizer {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern DUBAI_PREFIX_PATTERN = Pattern.compile("(?i)(dubai\\s*)");

    private PlateNumberNormalizer() {
    }

    public static String normalize(String rawText) {
        if (rawText == null) {
            return null;
        }

        String cleaned = rawText
                .replaceAll("[\\n\\r]+", " ")
                .replaceAll("[^A-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        cleaned = DUBAI_PREFIX_PATTERN.matcher(cleaned).replaceAll("");

        Matcher matcher = ALPHANUMERIC_PATTERN.matcher(cleaned);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(matcher.group().toUpperCase(Locale.ROOT));
        }

        return builder.length() == 0 ? null : builder.toString();
    }
}
