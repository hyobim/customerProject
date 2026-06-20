package com.hyundai.test.address.service;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerSearchRequest;
import com.hyundai.test.address.exception.CustomerNotFoundException;
import com.hyundai.test.address.exception.InvalidSearchConditionException;
import com.hyundai.test.address.repository.InMemoryCustomerRepository;
import com.hyundai.test.address.validation.CustomerValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressBookServiceTest {

    private AddressBookService service;

    @BeforeEach
    void setUp() {
        service = new AddressBookService(
                new InMemoryCustomerRepository(),
                new CustomerValidator()
        );
        service.add(new Customer("서울시 광진구", "01000000000", "hong@hyundai.com", "홍길동"));
        service.add(new Customer("경기도 성남시", "010-000-0001", "lee@hyundai.com", "이몽룡"));
    }

    @Test
    void 여러_조회_조건을_AND로_결합하고_정렬한다() {
        assertThat(service.search(new CustomerSearchRequest(
                        null, null, "시", "이", "name", "desc")))
                .extracting(Customer::name)
                .containsExactly("이몽룡");
    }

    @Test
    void 전화번호를_포함한_고객정보_전체를_수정한다() {
        var change = service.update(
                "01000000000",
                new Customer("서울시 중구", "01012345678", "new@hyundai.com", "홍길동")
        );

        assertThat(change.before().phoneNumber()).isEqualTo("010-0000-0000");
        assertThat(change.after().phoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    void 삭제_조건이_두개면_거부한다() {
        assertThatThrownBy(() -> service.delete(null, null, "서울", "홍"))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    @Test
    void 수정_대상이_없으면_예외를_반환한다() {
        assertThatThrownBy(() -> service.update(
                "01099999999",
                new Customer("서울", "01099999999", "none@hyundai.com", "없음")
        )).isInstanceOf(CustomerNotFoundException.class);
    }
}
