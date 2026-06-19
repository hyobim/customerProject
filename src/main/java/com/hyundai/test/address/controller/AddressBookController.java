package com.hyundai.test.address.controller;

import com.hyundai.test.address.controller.dto.CustomerChangeResponse;
import com.hyundai.test.address.controller.dto.CustomerRequest;
import com.hyundai.test.address.controller.dto.CustomerResponse;
import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.service.AddressBookService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class AddressBookController {

    private final AddressBookService service;

    public AddressBookController(AddressBookService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerResponse> search(
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction
    ) {
        return service.search(phoneNumber, email, address, name, sortBy, direction)
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @PutMapping("/{phoneNumber}")
    public CustomerChangeResponse update(
            @PathVariable String phoneNumber,
            @Valid @RequestBody CustomerRequest request
    ) {
        return CustomerChangeResponse.from(
                service.update(phoneNumber, request.toCustomer()));
    }

    @DeleteMapping
    public List<CustomerResponse> delete(
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String name
    ) {
        return service.delete(phoneNumber, email, address, name)
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }
}
