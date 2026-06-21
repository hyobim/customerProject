package com.hyundai.test.address.controller.dto;

import com.hyundai.test.address.domain.CustomerChange;
import lombok.Builder;

@Builder
public record CustomerChangeResponse(
        CustomerResponse before,
        CustomerResponse after
) {
    public static CustomerChangeResponse from(CustomerChange change) {
        return CustomerChangeResponse.builder()
                .before(CustomerResponse.from(change.before()))
                .after(CustomerResponse.from(change.after()))
                .build();
    }
}
