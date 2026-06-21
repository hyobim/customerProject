package com.hyundai.test.address.service.dto;

import lombok.Builder;

@Builder
public record CustomerSearchRequest(
        String phoneNumber,
        String email,
        String address,
        String name,
        String sortBy,
        String direction
) {
}
