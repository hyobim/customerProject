package com.hyundai.test.address.repository;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerChange;
import com.hyundai.test.address.domain.CustomerSearchCondition;
import com.hyundai.test.address.domain.SortDirection;
import com.hyundai.test.address.domain.SortField;
import com.hyundai.test.address.exception.CustomerNotFoundException;
import com.hyundai.test.address.exception.DuplicateCustomerException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

@Repository
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<String, Customer> customersByPhone = new HashMap<>();
    private final Map<String, String> phoneByEmail = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    @Override
    public void add(Customer customer) {
        lock.writeLock().lock();
        try {
            ensurePhoneAvailable(customer.phoneNumber(), null);
            ensureEmailAvailable(customer.email(), null);
            customersByPhone.put(customer.phoneNumber(), customer);
            phoneByEmail.put(customer.email(), customer.phoneNumber());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Customer> search(CustomerSearchCondition condition) {
        List<Customer> candidates;
        lock.readLock().lock();
        try {
            if (hasText(condition.phoneNumber())) {
                Customer customer = customersByPhone.get(condition.phoneNumber());
                candidates = customer == null ? List.of() : List.of(customer);
            } else if (hasText(condition.email())) {
                String phoneNumber = phoneByEmail.get(condition.email());
                Customer customer = phoneNumber == null ? null : customersByPhone.get(phoneNumber);
                candidates = customer == null ? List.of() : List.of(customer);
            } else {
                candidates = new ArrayList<>(customersByPhone.values());
            }
        } finally {
            lock.readLock().unlock();
        }

        return candidates.stream()
                .filter(customer -> matches(customer, condition))
                .sorted(comparator(condition.sortField(), condition.sortDirection()))
                .toList();
    }

    @Override
    public CustomerChange update(String currentPhoneNumber, Customer replacement) {
        lock.writeLock().lock();
        try {
            Customer current = customersByPhone.get(currentPhoneNumber);
            if (current == null) {
                throw new CustomerNotFoundException("수정할 고객을 찾을 수 없습니다.");
            }

            ensurePhoneAvailable(replacement.phoneNumber(), currentPhoneNumber);
            ensureEmailAvailable(replacement.email(), currentPhoneNumber);

            customersByPhone.remove(currentPhoneNumber);
            phoneByEmail.remove(current.email());
            customersByPhone.put(replacement.phoneNumber(), replacement);
            phoneByEmail.put(replacement.email(), replacement.phoneNumber());
            return new CustomerChange(current, replacement);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Customer> delete(CustomerSearchCondition condition) {
        lock.writeLock().lock();
        try {
            List<Customer> deleted = customersByPhone.values().stream()
                    .filter(customer -> matches(customer, condition))
                    .sorted(comparator(condition.sortField(), condition.sortDirection()))
                    .toList();

            if (deleted.isEmpty()) {
                throw new CustomerNotFoundException("삭제할 고객을 찾을 수 없습니다.");
            }
            deleted.forEach(customer -> {
                customersByPhone.remove(customer.phoneNumber());
                phoneByEmail.remove(customer.email());
            });
            return deleted;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Customer> snapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(customersByPhone.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    private void ensurePhoneAvailable(String phoneNumber, String currentPhoneNumber) {
        if (customersByPhone.containsKey(phoneNumber) && !phoneNumber.equals(currentPhoneNumber)) {
            throw new DuplicateCustomerException("이미 등록된 전화번호입니다.");
        }
    }

    private void ensureEmailAvailable(String email, String currentPhoneNumber) {
        String ownerPhone = phoneByEmail.get(email);
        if (ownerPhone != null && !ownerPhone.equals(currentPhoneNumber)) {
            throw new DuplicateCustomerException("이미 등록된 이메일입니다.");
        }
    }

    private boolean matches(Customer customer, CustomerSearchCondition condition) {
        return (!hasText(condition.phoneNumber()) || customer.phoneNumber().equals(condition.phoneNumber()))
                && (!hasText(condition.email()) || customer.email().equals(condition.email()))
                && (!hasText(condition.address()) || containsIgnoreCase(customer.address(), condition.address()))
                && (!hasText(condition.name()) || containsIgnoreCase(customer.name(), condition.name()));
    }

    private boolean containsIgnoreCase(String source, String target) {
        return source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }

    private Comparator<Customer> comparator(SortField field, SortDirection direction) {
        Function<Customer, String> extractor = switch (field) {
            case PHONE_NUMBER -> Customer::phoneNumber;
            case EMAIL -> Customer::email;
            case ADDRESS -> Customer::address;
            case NAME -> Customer::name;
        };
        Comparator<Customer> comparator = Comparator.comparing(extractor);
        return direction == SortDirection.DESC ? comparator.reversed() : comparator;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
