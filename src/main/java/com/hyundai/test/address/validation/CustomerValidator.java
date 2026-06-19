package com.hyundai.test.address.validation;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.exception.InvalidCustomerException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class CustomerValidator {

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(010\\d{7,8}|010-\\d{3}-\\d{4}|010-\\d{4}-\\d{4})$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+$");

    public Customer validateAndNormalize(Customer customer) {
        if (customer == null) {
            throw new InvalidCustomerException("고객정보는 필수입니다.");
        }

        String address = requireText(customer.address(), "주소");
        String phoneNumber = normalizePhoneNumber(requireText(customer.phoneNumber(), "전화번호"));
        String email = requireText(customer.email(), "이메일");
        String name = requireText(customer.name(), "이름");

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidCustomerException("이메일은 아이디@도메인 형식이어야 합니다.");
        }
        return new Customer(address, phoneNumber, email, name);
    }

    public String normalizePhoneNumber(String phoneNumber) {
        String value = requireText(phoneNumber, "전화번호");
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new InvalidCustomerException(
                    "전화번호는 0101231234, 010-123-1234 또는 010-1234-1234 형식이어야 합니다.");
        }

        String digits = value.replace("-", "");
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
    }

    public String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidCustomerException(fieldName + "은(는) 필수입니다.");
        }
        return value.trim();
    }
}
