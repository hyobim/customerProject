package com.hyundai.test.address.controller.dto;

import com.hyundai.test.address.domain.Customer;

public record CustomerResponse(
        String address,
        String phoneNumber,
        String email,
        String name
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.address(),
                customer.phoneNumber(),
                customer.email(),
                customer.name()
        );
    }
}
