package com.hyundai.test.address.domain;

import com.hyundai.test.address.exception.InvalidSearchConditionException;

import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return ASC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidSearchConditionException("지원하지 않는 정렬 방향입니다: " + value);
        }
    }
}
