package com.hyundai.test.address.validation;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.exception.InvalidCustomerException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerValidatorTest {

    private final CustomerValidator validator = new CustomerValidator();

    @Test
    void 허용된_전화번호를_표준_형식으로_변환한다() {
        assertThat(validator.normalizePhoneNumber("0101231234"))
                .isEqualTo("010-123-1234");
        assertThat(validator.normalizePhoneNumber("010-1234-1234"))
                .isEqualTo("010-1234-1234");
    }

    @Test
    void 고객정보를_검증하고_앞뒤_공백을_제거한다() {
        Customer customer = validator.validateAndNormalize(
                new Customer(" 서울 ", "01012345678", "user@example.com", " 이름 ")
        );

        assertThat(customer.address()).isEqualTo("서울");
        assertThat(customer.name()).isEqualTo("이름");
    }

    @Test
    void 잘못된_전화번호를_거부한다() {
        assertThatThrownBy(() -> validator.normalizePhoneNumber("01112345678"))
                .isInstanceOf(InvalidCustomerException.class);
    }

    @Test
    void 공백이_포함된_이메일을_거부한다() {
        assertThatThrownBy(() -> validator.validateAndNormalize(
                new Customer("서울", "01012345678", "user @example.com", "이름")
        )).isInstanceOf(InvalidCustomerException.class);
    }
}
