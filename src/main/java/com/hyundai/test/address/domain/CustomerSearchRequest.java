package com.hyundai.test.address.domain;

public record CustomerSearchRequest(
        String phoneNumber,
        String email,
        String address,
        String name,
        String sortBy,
        String direction
) {
}
