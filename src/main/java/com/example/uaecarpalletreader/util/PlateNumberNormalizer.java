package com.example.uaecarpalletreader.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlateNumberNormalizer {

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[A-Za-z0-9]+");
    private static final List<EmiratePattern> EMIRATE_PATTERNS = List.of(
            new EmiratePattern(List.of("ABU", "DHABI"), "Abu Dhabi"),
            new EmiratePattern(List.of("ABUDHABI"), "Abu Dhabi"),
            new EmiratePattern(List.of("DUBAI"), "Dubai"),
            new EmiratePattern(List.of("DXB"), "Dubai"),
            new EmiratePattern(List.of("SHARJAH"), "Sharjah"),
            new EmiratePattern(List.of("SHJ"), "Sharjah"),
            new EmiratePattern(List.of("AJMAN"), "Ajman"),
            new EmiratePattern(List.of("AJM"), "Ajman"),
            new EmiratePattern(List.of("UMM", "AL", "QUWAIN"), "Umm Al Quwain"),
            new EmiratePattern(List.of("UAQ"), "Umm Al Quwain"),
            new EmiratePattern(List.of("RAS", "AL", "KHAIMAH"), "Ras Al Khaimah"),
            new EmiratePattern(List.of("RAK"), "Ras Al Khaimah"),
            new EmiratePattern(List.of("FUJAIRAH"), "Fujairah"),
            new EmiratePattern(List.of("FUJ"), "Fujairah")
    );

    private PlateNumberNormalizer() {
    }

    public static NormalizedPlate normalize(String rawText) {
        if (rawText == null) {
            return new NormalizedPlate(null, null, null, null);
        }

        String cleaned = rawText
                .replaceAll("[\\n\\r]+", " ")
                .replaceAll("[^A-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return new NormalizedPlate(null, null, null, null);
        }

        Matcher matcher = ALPHANUMERIC_PATTERN.matcher(cleaned);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group().toUpperCase(Locale.ROOT));
        }

        if (tokens.isEmpty()) {
            return new NormalizedPlate(null, null, null, null);
        }

        EmirateMatch emirateMatch = findEmirate(tokens);
        String city = emirateMatch != null ? emirateMatch.emirate() : null;

        List<String> filteredTokens = new ArrayList<>(tokens);
        if (emirateMatch != null) {
            for (int i = 0; i < emirateMatch.length(); i++) {
                filteredTokens.set(emirateMatch.startIndex() + i, null);
            }
        }
        filteredTokens.removeIf(Objects::isNull);

        if (filteredTokens.isEmpty()) {
            return new NormalizedPlate(null, city, null, null);
        }

        String normalized = String.join(" ", filteredTokens);
        String characters = joinTokens(filteredTokens.stream()
                .filter(token -> token.chars().allMatch(Character::isLetter))
                .toList());

        String number = joinTokens(filteredTokens.stream()
                .filter(token -> token.chars().allMatch(Character::isDigit))
                .toList());

        return new NormalizedPlate(normalized, city,
                characters.isBlank() ? null : characters,
                number.isBlank() ? null : number);
    }

    private static EmirateMatch findEmirate(List<String> tokens) {
        for (EmiratePattern pattern : EMIRATE_PATTERNS) {
            EmirateMatch match = findPattern(tokens, pattern);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static EmirateMatch findPattern(List<String> tokens, EmiratePattern pattern) {
        List<String> patternTokens = pattern.tokens();
        int maxStart = tokens.size() - patternTokens.size();
        for (int start = 0; start <= maxStart; start++) {
            boolean matches = true;
            for (int offset = 0; offset < patternTokens.size(); offset++) {
                if (!Objects.equals(tokens.get(start + offset), patternTokens.get(offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return new EmirateMatch(pattern.displayName(), start, patternTokens.size());
            }
        }
        return null;
    }

    private static String joinTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return "";
        }
        return String.join(" ", tokens);
    }

    private record EmiratePattern(List<String> tokens, String displayName) {
    }

    public record NormalizedPlate(String normalizedPlate, String city, String letters, String number) {
    }

    private record EmirateMatch(String emirate, int startIndex, int length) {
    }
}
