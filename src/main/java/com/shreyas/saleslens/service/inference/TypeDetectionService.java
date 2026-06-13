package com.shreyas.saleslens.service.inference;

import com.shreyas.saleslens.model.enums.InferredType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class TypeDetectionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\-\\s().]{7,20}$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[\\$€£¥]\\s*\\d+(\\.\\d{1,2})?$|^\\d+(\\.\\d{1,2})?\\s*[\\$€£¥]$");

    public InferredType detectType(List<String> sampleValues) {
        if (sampleValues == null) {
            return InferredType.FREE_TEXT;
        }

        List<String> nonNullSamples = sampleValues.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();

        if (nonNullSamples.isEmpty()) {
            return InferredType.FREE_TEXT;
        }

        if (nonNullSamples.stream().allMatch(TypeDetectionService::isInteger)) {
            return InferredType.INTEGER;
        }

        if (nonNullSamples.stream().allMatch(TypeDetectionService::isDecimal)) {
            return InferredType.DECIMAL;
        }

        if (nonNullSamples.stream().allMatch(TypeDetectionService::isBoolean)) {
            return InferredType.BOOLEAN;
        }

        String dateFormat = detectDateFormat(nonNullSamples);
        if (dateFormat != null) {
            if ("yyyy-MM-dd'T'HH:mm:ss".equals(dateFormat)) {
                return InferredType.DATETIME;
            }
            return InferredType.DATE;
        }

        if (nonNullSamples.stream().allMatch(TypeDetectionService::isDateTime)) {
            return InferredType.DATETIME;
        }

        if (nonNullSamples.stream().allMatch(v -> EMAIL_PATTERN.matcher(v).matches())) {
            return InferredType.EMAIL;
        }

        if (nonNullSamples.stream().allMatch(v -> PHONE_PATTERN.matcher(v).matches())) {
            return InferredType.PHONE;
        }

        if (nonNullSamples.stream().allMatch(v -> CURRENCY_PATTERN.matcher(v).matches())) {
            return InferredType.CURRENCY_AMOUNT;
        }

        long uniqueCount = nonNullSamples.stream().distinct().count();
        if (uniqueCount < 20) {
            return InferredType.CATEGORY;
        }

        return InferredType.FREE_TEXT;
    }

    public String detectDateFormat(List<String> sampleValues) {
        if (sampleValues == null) {
            return null;
        }

        List<String> nonNullSamples = sampleValues.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .toList();

        if (nonNullSamples.isEmpty()) {
            return null;
        }

        String[] patterns = {
                "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd'T'HH:mm:ss",
                "MMM d, yyyy", "dd-MMM-yyyy", "yyyy/MM/dd", "dd.MM.yyyy"
        };

        for (String pattern : patterns) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
            boolean allParse = true;
            for (String val : nonNullSamples) {
                try {
                    formatter.parse(val);
                } catch (Exception e) {
                    allParse = false;
                    break;
                }
            }
            if (allParse) {
                return pattern;
            }
        }
        return null;
    }

    private static boolean isInteger(String value) {
        String normalized = value;
        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.length() > 1 && normalized.startsWith("0")) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDecimal(String value) {
        String normalized = value;
        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.length() > 1 && normalized.startsWith("0") && normalized.charAt(1) != '.') {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isDateTime(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (Exception e1) {
            try {
                DateTimeFormatter.ISO_DATE_TIME.parse(value);
                return true;
            } catch (Exception e2) {
                try {
                    LocalDateTime.parse(value);
                    return true;
                } catch (Exception e3) {
                    try {
                        OffsetDateTime.parse(value);
                        return true;
                    } catch (Exception e4) {
                        try {
                            ZonedDateTime.parse(value);
                            return true;
                        } catch (Exception e5) {
                            return false;
                        }
                    }
                }
            }
        }
    }
}
