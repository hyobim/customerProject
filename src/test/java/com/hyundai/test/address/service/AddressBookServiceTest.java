package com.hyundai.test.address.service;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.exception.CustomerNotFoundException;
import com.hyundai.test.address.exception.InvalidCustomerException;
import com.hyundai.test.address.exception.InvalidSearchConditionException;
import com.hyundai.test.address.repository.InMemoryCustomerRepository;
import com.hyundai.test.address.service.dto.CustomerSearchRequest;
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
        service.add(new Customer(
                "\uC11C\uC6B8 \uAD11\uC9C4\uAD6C",
                "01000000000",
                "hong@hyundai.com",
                "\uD64D\uAE38\uB3D9"
        ));
        service.add(new Customer(
                "\uACBD\uAE30 \uC131\uB0A8\uC2DC",
                "010-000-0001",
                "lee@hyundai.com",
                "\uC774\uBAA8\uBC14"
        ));
    }

    @Test
    void combines_search_filters_with_and_and_sorts_results() {
        assertThat(service.search(CustomerSearchRequest.builder()
                        .address("\uC11C\uC6B8")
                        .name("\uD64D")
                        .sortBy("name")
                        .direction("desc")
                        .build()))
                .extracting(Customer::name)
                .containsExactly("\uD64D\uAE38\uB3D9");
    }

    @Test
    void updates_full_customer_information_including_phone_number() {
        var change = service.update(
                "01000000000",
                new Customer("\uC11C\uC6B8 \uC911\uAD6C", "01012345678", "new@hyundai.com", "\uD64D\uAE38\uB3D9")
        );

        assertThat(change.before().phoneNumber()).isEqualTo("010-0000-0000");
        assertThat(change.after().phoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    void rejects_delete_when_multiple_filters_are_provided() {
        assertThatThrownBy(() -> service.delete(null, null, "\uC11C\uC6B8", "\uD64D"))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    @Test
    void rejects_delete_when_phone_number_is_blank() {
        assertThatThrownBy(() -> service.delete("", null, null, null))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    @Test
    void rejects_delete_when_email_is_blank() {
        assertThatThrownBy(() -> service.delete(null, "   ", null, null))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    @Test
    void rejects_delete_when_address_or_name_is_blank() {
        assertThatThrownBy(() -> service.delete(null, null, "", null))
                .isInstanceOf(InvalidSearchConditionException.class);
        assertThatThrownBy(() -> service.delete(null, null, null, "   "))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    @Test
    void rejects_delete_when_valid_and_blank_filters_are_mixed() {
        assertThatThrownBy(() -> service.delete("01000000000", null, "   ", null))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE);
    }

    @Test
    void throws_not_found_when_update_target_does_not_exist() {
        assertThatThrownBy(() -> service.update(
                "01099999999",
                new Customer("\uC11C\uC6B8", "01099999999", "none@hyundai.com", "\uC5C6\uC74C")
        )).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void rejects_invalid_email_for_search() {
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .email("invalid-email")
                        .build()))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage(CustomerValidator.EMAIL_FORMAT_MESSAGE);
    }

    @Test
    void rejects_blank_email_for_search() {
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .email("   ")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE);
    }

    @Test
    void rejects_blank_phone_address_and_name_for_search() {
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .phoneNumber(" ")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class);
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .address("")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class);
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .name("   ")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    @Test
    void rejects_empty_and_blank_sort_parameters_for_search() {
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .sortBy("")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE);
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .sortBy("   ")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE);
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .direction("")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE);
        assertThatThrownBy(() -> service.search(CustomerSearchRequest.builder()
                        .direction("   ")
                        .build()))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE);
    }

    @Test
    void keeps_default_and_valid_sort_behavior() {
        assertThat(service.search(CustomerSearchRequest.builder().build()))
                .extracting(Customer::phoneNumber)
                .containsExactly("010-000-0001", "010-0000-0000");

        assertThat(service.search(CustomerSearchRequest.builder()
                        .sortBy("name")
                        .direction("desc")
                        .build()))
                .extracting(Customer::name)
                .containsExactly("\uD64D\uAE38\uB3D9", "\uC774\uBAA8\uBC14");
    }

    @Test
    void rejects_invalid_email_for_delete() {
        assertThatThrownBy(() -> service.delete(null, "invalid-email", null, null))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage(CustomerValidator.EMAIL_FORMAT_MESSAGE);
    }

    @Test
    void returns_empty_list_for_missing_but_valid_search_email() {
        assertThat(service.search(CustomerSearchRequest.builder()
                        .email("missing@example.com")
                        .build()))
                .isEmpty();
    }

    @Test
    void throws_not_found_for_missing_but_valid_delete_email() {
        assertThatThrownBy(() -> service.delete(null, "missing@example.com", null, null))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void keeps_existing_behavior_when_email_filter_is_absent() {
        assertThat(service.search(CustomerSearchRequest.builder().build()))
                .hasSize(2);
    }
}
