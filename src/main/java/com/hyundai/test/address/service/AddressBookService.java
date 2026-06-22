package com.hyundai.test.address.service;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerChange;
import com.hyundai.test.address.domain.CustomerSearchCondition;
import com.hyundai.test.address.domain.SortDirection;
import com.hyundai.test.address.domain.SortField;
import com.hyundai.test.address.exception.InvalidSearchConditionException;
import com.hyundai.test.address.repository.CustomerRepository;
import com.hyundai.test.address.service.dto.CustomerSearchRequest;
import com.hyundai.test.address.validation.CustomerValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AddressBookService {

    public static final String DELETE_CONDITION_MESSAGE =
            "\uC0AD\uC81C \uC870\uAC74\uC740 \uBE44\uC5B4 \uC788\uC9C0 \uC54A\uC740 \uAC12\uC73C\uB85C \uC815\uD655\uD788 \uD558\uB098\uB9CC \uC9C0\uC815\uD574\uC57C \uD569\uB2C8\uB2E4.";
    public static final String EMPTY_SEARCH_CONDITION_MESSAGE =
            "\uAC80\uC0C9 \uC870\uAC74\uC740 \uBE48 \uAC12\uC774\uAC70\uB098 \uACF5\uBC31\uB9CC\uC73C\uB85C \uAD6C\uC131\uB420 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.";

    private final CustomerRepository repository;
    private final CustomerValidator validator;

    public List<Customer> search(CustomerSearchRequest request) {
        CustomerSearchCondition condition = condition(
                request.phoneNumber(),
                request.email(),
                request.address(),
                request.name(),
                request.sortBy(),
                request.direction()
        );
        return repository.search(condition);
    }

    public CustomerChange update(String currentPhoneNumber, Customer replacement) {
        String normalizedCurrentPhone = validator.normalizePhoneNumber(currentPhoneNumber);
        Customer normalizedReplacement = validator.validateAndNormalize(replacement);
        return repository.update(normalizedCurrentPhone, normalizedReplacement);
    }

    public List<Customer> delete(String phoneNumber, String email, String address, String name) {
        long filterCount = Stream.of(phoneNumber, email, address, name)
                .filter(this::hasText)
                .count();
        if (filterCount != 1) {
            throw new InvalidSearchConditionException(DELETE_CONDITION_MESSAGE);
        }
        return repository.delete(condition(phoneNumber, email, address, name, null, null));
    }

    public void add(Customer customer) {
        repository.add(validator.validateAndNormalize(customer));
    }

    public List<Customer> snapshot() {
        return repository.snapshot();
    }

    private CustomerSearchCondition condition(
            String phoneNumber,
            String email,
            String address,
            String name,
            String sortBy,
            String direction
    ) {
        validateSearchValue(phoneNumber);
        validateSearchValue(email);
        validateSearchValue(address);
        validateSearchValue(name);

        String normalizedPhone = phoneNumber != null
                ? validator.normalizePhoneNumber(phoneNumber)
                : null;
        String normalizedEmail = email == null
                ? null
                : validator.normalizeEmail(email);
        String normalizedAddress = address == null
                ? null
                : validator.requireText(address, "\uC8FC\uC18C");
        String normalizedName = name == null
                ? null
                : validator.requireText(name, "\uC774\uB984");

        return new CustomerSearchCondition(
                normalizedPhone,
                normalizedEmail,
                normalizedAddress,
                normalizedName,
                SortField.from(sortBy),
                SortDirection.from(direction)
        );
    }

    private void validateSearchValue(String value) {
        if (value != null && value.isBlank()) {
            throw new InvalidSearchConditionException(EMPTY_SEARCH_CONDITION_MESSAGE);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
