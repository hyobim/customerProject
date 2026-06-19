package com.hyundai.test.address.domain;

public record CustomerSearchCondition(
        String phoneNumber,
        String email,
        String address,
        String name,
        SortField sortField,
        SortDirection sortDirection
) {
    public CustomerSearchCondition {
        sortField = sortField == null ? SortField.PHONE_NUMBER : sortField;
        sortDirection = sortDirection == null ? SortDirection.ASC : sortDirection;
    }
}
