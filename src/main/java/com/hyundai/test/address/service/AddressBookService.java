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
            throw new InvalidSearchConditionException("삭제 조건은 정확히 하나만 지정해야 합니다.");
        }
        return repository.delete(condition(
                phoneNumber, email, address, name, null, null));
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
        String normalizedPhone = hasText(phoneNumber)
                ? validator.normalizePhoneNumber(phoneNumber)
                : null;
        String normalizedEmail = hasText(email) ? email.trim() : null;
        String normalizedAddress = hasText(address) ? address.trim() : null;
        String normalizedName = hasText(name) ? name.trim() : null;

        return new CustomerSearchCondition(
                normalizedPhone,
                normalizedEmail,
                normalizedAddress,
                normalizedName,
                SortField.from(sortBy),
                SortDirection.from(direction)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
