package com.hyundai.test.address.validation;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.exception.InvalidCustomerException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerValidatorTest {

    private final CustomerValidator validator = new CustomerValidator();

    @Test
    void normalizes_phone_number_formats() {
        assertThat(validator.normalizePhoneNumber("0101231234"))
                .isEqualTo("010-123-1234");
        assertThat(validator.normalizePhoneNumber("010-1234-1234"))
                .isEqualTo("010-1234-1234");
    }

    @Test
    void validates_customer_and_trims_surrounding_spaces() {
        Customer customer = validator.validateAndNormalize(
                new Customer(" \uC11C\uC6B8 ", "01012345678", " user@example.com ", " \uD64D\uAE38\uB3D9 ")
        );

        assertThat(customer.address()).isEqualTo("\uC11C\uC6B8");
        assertThat(customer.email()).isEqualTo("user@example.com");
        assertThat(customer.name()).isEqualTo("\uD64D\uAE38\uB3D9");
    }

    @Test
    void rejects_invalid_phone_number() {
        assertThatThrownBy(() -> validator.normalizePhoneNumber("01112345678"))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage(CustomerValidator.PHONE_FORMAT_MESSAGE);
    }

    @Test
    void rejects_invalid_email_format() {
        assertThatThrownBy(() -> validator.normalizeEmail("user @example.com"))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage(CustomerValidator.EMAIL_FORMAT_MESSAGE);
    }

    @Test
    void rejects_blank_email() {
        assertThatThrownBy(() -> validator.normalizeEmail("   "))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage("\uC774\uBA54\uC77C\uC740(\uB294) \uD544\uC218\uC785\uB2C8\uB2E4.");
    }

    @Test
    void preserves_email_case() {
        assertThat(validator.normalizeEmail("User@Example.com"))
                .isEqualTo("User@Example.com");
    }
}
