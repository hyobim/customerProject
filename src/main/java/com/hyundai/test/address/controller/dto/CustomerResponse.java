package com.hyundai.test.address.controller.dto;

import com.hyundai.test.address.domain.Customer;
import lombok.Builder;

@Builder
public record CustomerResponse(
        String address,
        String phoneNumber,
        String email,
        String name
) {
    public static CustomerResponse from(Customer customer) {
        return CustomerResponse.builder()
                .address(customer.address())
                .phoneNumber(customer.phoneNumber())
                .email(customer.email())
                .name(customer.name())
                .build();
    }
}
