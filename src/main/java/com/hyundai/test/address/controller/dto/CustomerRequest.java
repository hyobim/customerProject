package com.hyundai.test.address.controller.dto;

import com.hyundai.test.address.domain.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerRequest(
        @NotBlank(message = "주소는 필수입니다.")
        String address,
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = "^(010\\d{7,8}|010-\\d{3}-\\d{4}|010-\\d{4}-\\d{4})$",
                message = "전화번호 형식이 올바르지 않습니다."
        )
        String phoneNumber,
        @NotBlank(message = "이메일은 필수입니다.")
        @Pattern(
                regexp = "^[^\\s@]+@[^\\s@]+$",
                message = "이메일은 아이디@도메인 형식이어야 합니다."
        )
        String email,
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
    public Customer toCustomer() {
        return new Customer(address, phoneNumber, email, name);
    }
}
