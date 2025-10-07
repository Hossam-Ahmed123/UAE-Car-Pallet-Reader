package com.uae.anpr.service.parser;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Utility component that derives structured UAE plate attributes from OCR text.
 */
@Component
public class UaePlateParser {

    private static final List<CityPattern> CITY_PATTERNS = List.of(
            new CityPattern("ABUDHABI", "Abu Dhabi"),
            new CityPattern("AUH", "Abu Dhabi"),
            new CityPattern("ALAIN", "Al Ain"),
            new CityPattern("DUBAI", "Dubai"),
            new CityPattern("DXB", "Dubai"),
            new CityPattern("SHARJAH", "Sharjah"),
            new CityPattern("SHJ", "Sharjah"),
            new CityPattern("AJMAN", "Ajman"),
            new CityPattern("AJM", "Ajman"),
            new CityPattern("UMMALQUWAIN", "Umm Al Quwain"),
            new CityPattern("UAQ", "Umm Al Quwain"),
            new CityPattern("RASALKHAIMAH", "Ras Al Khaimah"),
            new CityPattern("RAK", "Ras Al Khaimah"),
            new CityPattern("FUJAIRAH", "Fujairah"),
            new CityPattern("FJR", "Fujairah")
    );

    /**
     * Parse the OCR text into structured plate details.
     *
     * @param plateText normalized OCR output consisting of alphanumeric characters
     * @return structured breakdown of the plate text
     */
    public PlateBreakdown parse(String plateText) {
        if (plateText == null || plateText.isBlank()) {
            return PlateBreakdown.empty();
        }

        String normalized = plateText.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.isEmpty()) {
            return PlateBreakdown.empty();
        }

        String digits = normalized.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        int firstDigitIndex = indexOfFirstDigit(normalized);
        int lastDigitIndex = indexOfLastDigit(normalized);

        String letterPrefix = firstDigitIndex >= 0
                ? normalized.substring(0, firstDigitIndex)
                : normalized;
        letterPrefix = letterPrefix.replaceAll("[^A-Z]", "");

        String letterSuffix = lastDigitIndex >= 0 && lastDigitIndex < normalized.length() - 1
                ? normalized.substring(lastDigitIndex + 1)
                : "";
        letterSuffix = letterSuffix.replaceAll("[^A-Z]", "");

        CityPattern cityMatch = resolveCity(letterPrefix, normalized);
        if (cityMatch == null) {
            cityMatch = resolveCity(letterSuffix, normalized);
        }

        String city = cityMatch != null ? cityMatch.city() : null;
        String plateCharacter = deriveClassification(letterPrefix, letterSuffix, cityMatch);
        String carNumber = digits.isEmpty() ? null : digits;

        return new PlateBreakdown(city, plateCharacter, carNumber);
    }

    private String deriveClassification(String prefixLetters, String suffixLetters, CityPattern cityMatch) {
        String candidate = normalizeClassification(prefixLetters, cityMatch);
        if (candidate != null) {
            return candidate;
        }
        candidate = normalizeClassification(suffixLetters, cityMatch);
        return candidate;
    }

    private String normalizeClassification(String value, CityPattern cityMatch) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value;
        if (cityMatch != null && stripped.startsWith(cityMatch.code())) {
            stripped = stripped.substring(cityMatch.code().length());
        }
        stripped = stripped.replaceAll("[^A-Z]", "");
        if (stripped.isBlank()) {
            return null;
        }
        return stripped;
    }

    private CityPattern resolveCity(String scopedLetters, String normalizedPlate) {
        if (scopedLetters != null && !scopedLetters.isBlank()) {
            Optional<CityPattern> scopedMatch = CITY_PATTERNS.stream()
                    .filter(pattern -> scopedLetters.startsWith(pattern.code()))
                    .max(Comparator.comparingInt(pattern -> pattern.code().length()));
            if (scopedMatch.isPresent()) {
                return scopedMatch.get();
            }
        }

        return CITY_PATTERNS.stream()
                .filter(pattern -> normalizedPlate.contains(pattern.code()))
                .max(Comparator.comparingInt(pattern -> pattern.code().length()))
                .orElse(null);
    }

    private int indexOfFirstDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfLastDigit(String value) {
        for (int i = value.length() - 1; i >= 0; i--) {
            if (Character.isDigit(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private record CityPattern(String code, String city) {
    }

    public record PlateBreakdown(String city, String plateCharacter, String carNumber) {

        private static final PlateBreakdown EMPTY = new PlateBreakdown(null, null, null);

        public static PlateBreakdown empty() {
            return EMPTY;
        }
    }
}
