package com.hyundai.test.address.controller.dto;

import com.hyundai.test.address.domain.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.hyundai.test.address.validation.CustomerValidator.EMAIL_FORMAT_MESSAGE;
import static com.hyundai.test.address.validation.CustomerValidator.EMAIL_REGEX;
import static com.hyundai.test.address.validation.CustomerValidator.PHONE_FORMAT_MESSAGE;
import static com.hyundai.test.address.validation.CustomerValidator.PHONE_REGEX;

public record CustomerRequest(
        @NotBlank(message = "주소는 필수입니다.")
        String address,
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(
                regexp = PHONE_REGEX,
                message = PHONE_FORMAT_MESSAGE
        )
        String phoneNumber,
        @NotBlank(message = "이메일은 필수입니다.")
        @Pattern(
                regexp = EMAIL_REGEX,
                message = EMAIL_FORMAT_MESSAGE
        )
        String email,
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
    public Customer toCustomer() {
        return new Customer(address, phoneNumber, email, name);
    }
}
