package com.hyundai.test.address.domain;

public record Customer(
        String address,
        String phoneNumber,
        String email,
        String name
) {
}
