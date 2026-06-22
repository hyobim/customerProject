package com.hyundai.test.address.controller;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.service.AddressBookService;
import com.hyundai.test.address.validation.CustomerValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "address.persistence.enabled=false")
@AutoConfigureMockMvc
class AddressBookApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AddressBookService service;

    @Test
    void wires_service_and_repository_for_search_update_and_delete() throws Exception {
        service.add(new Customer(
                "\uC11C\uC6B8 \uAD11\uC9C4\uAD6C",
                "01000000000",
                "hong@hyundai.com",
                "\uD64D\uAE38\uB3D9"
        ));

        mockMvc.perform(get("/api/customers")
                        .param("phoneNumber", "01000000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phoneNumber").value("010-0000-0000"));

        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "address": "\uC11C\uC6B8 \uC911\uAD6C",
                                  "phoneNumber": "01012345678",
                                  "email": "updated@hyundai.com",
                                  "name": "\uD64D\uAE38\uB3D9"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.after.phoneNumber").value("010-1234-5678"));

        mockMvc.perform(get("/api/customers")
                        .param("email", "updated@hyundai.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].address").value("\uC11C\uC6B8 \uC911\uAD6C"));

        mockMvc.perform(delete("/api/customers")
                        .param("phoneNumber", "01012345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("updated@hyundai.com"));

        mockMvc.perform(get("/api/customers")
                        .param("phoneNumber", "01012345678"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returns_bad_request_for_invalid_search_email() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .param("email", "invalid-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CustomerValidator.EMAIL_FORMAT_MESSAGE));
    }

    @Test
    void returns_bad_request_for_blank_search_email() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .param("email", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE));
    }

    @Test
    void returns_bad_request_for_invalid_delete_email() throws Exception {
        mockMvc.perform(delete("/api/customers")
                        .param("email", "invalid-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(CustomerValidator.EMAIL_FORMAT_MESSAGE));
    }

    @Test
    void returns_empty_array_for_missing_but_valid_search_email() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .param("email", "missing@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returns_not_found_for_missing_but_valid_delete_email() throws Exception {
        mockMvc.perform(delete("/api/customers")
                        .param("email", "missing@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blank_delete_condition_is_rejected_without_deleting_any_customer() throws Exception {
        service.add(new Customer(
                "\uC11C\uC6B8 \uC1A1\uD30C\uAD6C",
                "01055556666",
                "qa-one@hyundai.com",
                "\uD050\uC5D0\uC774\uC6D0"
        ));
        service.add(new Customer(
                "\uACBD\uAE30 \uC6A9\uC778\uC2DC",
                "01077778888",
                "qa-two@hyundai.com",
                "\uD050\uC5D0\uC774\uD22C"
        ));
        var before = service.snapshot();

        mockMvc.perform(delete("/api/customers")
                        .param("phoneNumber", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(service.snapshot())
                .containsExactlyInAnyOrderElementsOf(before);
    }
}
