package com.hyundai.test.address.validation;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.exception.InvalidCustomerException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class CustomerValidator {

    public static final String PHONE_REGEX =
            "^(010\\d{7,8}|010-\\d{3}-\\d{4}|010-\\d{4}-\\d{4})$";
    public static final String PHONE_FORMAT_MESSAGE =
            "\uC804\uD654\uBC88\uD638\uB294 0101231234, 010-123-1234 \uB610\uB294 010-1234-1234 \uD615\uC2DD\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4.";
    public static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+$";
    public static final String EMAIL_FORMAT_MESSAGE =
            "\uC774\uBA54\uC77C\uC740 \uC544\uC774\uB514@\uB3C4\uBA54\uC778 \uD615\uC2DD\uC774\uC5B4\uC57C \uD569\uB2C8\uB2E4.";

    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    public Customer validateAndNormalize(Customer customer) {
        if (customer == null) {
            throw new InvalidCustomerException("\uACE0\uAC1D\uC815\uBCF4\uB294 \uD544\uC218\uC785\uB2C8\uB2E4.");
        }

        String address = requireText(customer.address(), "\uC8FC\uC18C");
        String phoneNumber = normalizePhoneNumber(requireText(customer.phoneNumber(), "\uC804\uD654\uBC88\uD638"));
        String email = normalizeEmail(customer.email());
        String name = requireText(customer.name(), "\uC774\uB984");

        return new Customer(address, phoneNumber, email, name);
    }

    public String normalizeEmail(String email) {
        String value = requireText(email, "\uC774\uBA54\uC77C");
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new InvalidCustomerException(EMAIL_FORMAT_MESSAGE);
        }
        return value;
    }

    public String normalizePhoneNumber(String phoneNumber) {
        String value = requireText(phoneNumber, "\uC804\uD654\uBC88\uD638");
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new InvalidCustomerException(PHONE_FORMAT_MESSAGE);
        }

        String digits = value.replace("-", "");
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
    }

    public String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidCustomerException(fieldName + "\uC740(\uB294) \uD544\uC218\uC785\uB2C8\uB2E4.");
        }
        return value.trim();
    }
}
