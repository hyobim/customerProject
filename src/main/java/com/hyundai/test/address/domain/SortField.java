package com.hyundai.test.address.domain;

import com.hyundai.test.address.exception.InvalidSearchConditionException;

import java.util.Locale;

public enum SortField {
    PHONE_NUMBER,
    EMAIL,
    ADDRESS,
    NAME;

    public static SortField from(String value) {
        if (value == null || value.isBlank()) {
            return PHONE_NUMBER;
        }
        String normalized = value.trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "phone", "phonenumber" -> PHONE_NUMBER;
            case "email" -> EMAIL;
            case "address" -> ADDRESS;
            case "name" -> NAME;
            default -> throw new InvalidSearchConditionException(
                    "지원하지 않는 정렬 필드입니다: " + value);
        };
    }
}
