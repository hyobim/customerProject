package com.hyundai.test.address.controller.dto;

import com.hyundai.test.address.domain.CustomerChange;

public record CustomerChangeResponse(
        CustomerResponse before,
        CustomerResponse after
) {
    public static CustomerChangeResponse from(CustomerChange change) {
        return new CustomerChangeResponse(
                CustomerResponse.from(change.before()),
                CustomerResponse.from(change.after())
        );
    }
}
