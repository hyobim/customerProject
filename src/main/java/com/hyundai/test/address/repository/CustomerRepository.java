package com.hyundai.test.address.repository;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerChange;
import com.hyundai.test.address.domain.CustomerSearchCondition;

import java.util.List;

public interface CustomerRepository {
    void add(Customer customer);

    List<Customer> search(CustomerSearchCondition condition);

    CustomerChange update(String currentPhoneNumber, Customer replacement);

    List<Customer> delete(CustomerSearchCondition condition);

    List<Customer> snapshot();
}
