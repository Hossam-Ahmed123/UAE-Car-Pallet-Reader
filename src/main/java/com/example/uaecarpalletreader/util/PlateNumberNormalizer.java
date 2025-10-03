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

        List<String> compactTokens = collapseSingleDigitSequences(filteredTokens);

        List<String> relevantTokens = extractRelevantWindow(compactTokens);
        if (relevantTokens.isEmpty()) {
            return new NormalizedPlate(null, city, null, null);
        }

        List<String> prioritizedTokens = selectBestTokens(relevantTokens);
        if (prioritizedTokens.isEmpty()) {
            return new NormalizedPlate(null, city, null, null);
        }

        if (shouldReorderLettersFirst(prioritizedTokens)) {
            prioritizedTokens = reorderLettersFirst(prioritizedTokens);
        }

        String normalized = String.join(" ", prioritizedTokens);
        String characters = joinTokens(prioritizedTokens.stream()
                .filter(PlateNumberNormalizer::isLetterToken)
                .toList());

        String number = joinTokens(prioritizedTokens.stream()
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

    private static List<String> collapseSingleDigitSequences(List<String> tokens) {
        List<String> result = new ArrayList<>();
        StringBuilder digitBuffer = new StringBuilder();

        for (String token : tokens) {
            if (token.chars().allMatch(Character::isDigit)) {
                if (token.length() == 1) {
                    digitBuffer.append(token);
                    continue;
                }
                if (!digitBuffer.isEmpty()) {
                    result.add(digitBuffer.toString());
                    digitBuffer.setLength(0);
                }
                result.add(token);
            } else {
                if (!digitBuffer.isEmpty()) {
                    result.add(digitBuffer.toString());
                    digitBuffer.setLength(0);
                }
                result.add(token);
            }
        }

        if (!digitBuffer.isEmpty()) {
            result.add(digitBuffer.toString());
        }

        return result;
    }

    private static List<String> extractRelevantWindow(List<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }

        int firstIndex = -1;
        int lastIndex = -1;
        boolean hasDigit = false;
        int singleDigitTokenCount = 0;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            boolean containsDigit = token.chars().anyMatch(Character::isDigit);
            if (containsDigit) {
                hasDigit = true;
                if (token.chars().allMatch(Character::isDigit) && token.length() == 1) {
                    singleDigitTokenCount++;
                }
            }

            if (isSignificantDigitToken(token)) {
                if (firstIndex == -1) {
                    firstIndex = i;
                }
                lastIndex = i;
            }
        }

        if (firstIndex == -1) {
            if (hasDigit && singleDigitTokenCount >= 4) {
                return List.of();
            }
            return tokens;
        }

        int start = Math.max(0, firstIndex - 3);
        int end = Math.min(tokens.size() - 1, lastIndex + 3);

        return new ArrayList<>(tokens.subList(start, end + 1));
    }

    private static boolean isSignificantDigitToken(String token) {
        int digitCount = 0;
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                digitCount++;
            }
        }
        return digitCount >= 2;
    }

    private record EmiratePattern(List<String> tokens, String displayName) {
    }

    public record NormalizedPlate(String normalizedPlate, String city, String letters, String number) {
    }

    private record EmirateMatch(String emirate, int startIndex, int length) {
    }

    private static List<String> selectBestTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> best = List.of();
        int bestScore = Integer.MIN_VALUE;

        for (int start = 0; start < tokens.size(); start++) {
            for (int end = start; end < tokens.size(); end++) {
                List<String> candidate = tokens.subList(start, end + 1);
                int score = scoreCandidate(candidate);
                if (score == Integer.MIN_VALUE) {
                    continue;
                }

                if (score > bestScore || (score == bestScore && candidate.size() < best.size())) {
                    bestScore = score;
                    best = new ArrayList<>(candidate);
                }
            }
        }

        return best;
    }

    private static int scoreCandidate(List<String> candidate) {
        boolean hasDigit = false;
        boolean digitSeen = false;
        int score = 0;
        String previousToken = null;
        int lettersBeforeDigits = 0;
        int lettersAfterDigits = 0;

        for (String token : candidate) {
            boolean containsDigit = token.chars().anyMatch(Character::isDigit);
            boolean containsLetter = token.chars().anyMatch(Character::isLetter);

            if (containsDigit) {
                hasDigit = true;
                int digitCount = (int) token.chars().filter(Character::isDigit).count();
                score += digitCount * 5;
                if (containsLetter) {
                    score += 2;
                }
                digitSeen = true;
            }

            if (containsLetter && !containsDigit) {
                if (digitSeen) {
                    score += scoreLetterTokenAfterDigits(token, lettersAfterDigits);
                    lettersAfterDigits++;
                } else {
                    score += scoreLetterTokenBeforeDigits(token, lettersBeforeDigits);
                    lettersBeforeDigits++;
                }
            }

            if (previousToken != null && previousToken.equals(token)) {
                score -= 3;
            }
            previousToken = token;
        }

        if (!hasDigit) {
            return Integer.MIN_VALUE;
        }

        score -= candidate.size() * 2;
        return score;
    }

    private static int scoreLetterTokenBeforeDigits(String token, int index) {
        int length = token.length();
        int baseScore;
        if (length == 1) {
            baseScore = 4;
        } else if (length == 2) {
            baseScore = 6;
        } else if (length == 3) {
            baseScore = 3;
        } else {
            baseScore = -length;
        }

        if (length > 1 && isSingleRepeatedCharacter(token)) {
            baseScore -= 3;
        }

        if (index > 0) {
            baseScore -= index * 3;
        }
        return baseScore;
    }

    private static int scoreLetterTokenAfterDigits(String token, int index) {
        int length = token.length();
        int baseScore;
        if (length == 1) {
            baseScore = -5;
        } else if (length == 2) {
            baseScore = 3;
        } else if (length == 3) {
            baseScore = 1;
        } else {
            baseScore = -length;
        }

        if (length > 1 && isSingleRepeatedCharacter(token)) {
            baseScore -= 3;
        }

        if (index > 0) {
            baseScore -= index * 4;
        }
        return baseScore;
    }

    private static boolean isSingleRepeatedCharacter(String token) {
        char first = token.charAt(0);
        for (int i = 1; i < token.length(); i++) {
            if (token.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldReorderLettersFirst(List<String> tokens) {
        if (tokens.isEmpty()) {
            return false;
        }
        boolean firstIsNumber = tokens.get(0).chars().allMatch(Character::isDigit);
        if (!firstIsNumber) {
            return false;
        }
        return tokens.stream().anyMatch(PlateNumberNormalizer::isLetterToken);
    }

    private static List<String> reorderLettersFirst(List<String> tokens) {
        List<String> letters = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String token : tokens) {
            if (isLetterToken(token)) {
                letters.add(token);
            } else {
                others.add(token);
            }
        }
        List<String> reordered = new ArrayList<>(letters.size() + others.size());
        reordered.addAll(letters);
        reordered.addAll(others);
        return reordered;
    }

    private static boolean isLetterToken(String token) {
        return !token.isEmpty() && token.chars().allMatch(Character::isLetter);
    }
}
