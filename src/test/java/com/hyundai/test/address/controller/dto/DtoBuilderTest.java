package com.hyundai.test.address.controller.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DtoBuilderTest {

    @Test
    void record_DTO를_builder로_생성한다() {
        CustomerRequest request = CustomerRequest.builder()
                .address("서울")
                .phoneNumber("01012345678")
                .email("user@example.com")
                .name("홍길동")
                .build();
        CustomerResponse response = CustomerResponse.builder()
                .address(request.address())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .name(request.name())
                .build();
        CustomerChangeResponse change = CustomerChangeResponse.builder()
                .before(response)
                .after(response)
                .build();
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.EPOCH)
                .status(400)
                .error("Bad Request")
                .message("잘못된 요청")
                .path("/api/customers")
                .build();

        assertThat(change.before()).isEqualTo(response);
        assertThat(error.status()).isEqualTo(400);
    }
}
