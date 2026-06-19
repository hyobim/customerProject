package com.hyundai.test.address.controller;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.service.AddressBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "address.persistence.enabled=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AddressBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AddressBookService service;

    @BeforeEach
    void setUp() {
        service.add(new Customer("서울시 광진구", "01000000000", "hong@hyundai.com", "홍길동"));
    }

    @Test
    void 조회_결과를_JSON_배열로_반환한다() throws Exception {
        mockMvc.perform(get("/api/customers").param("name", "홍길"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phoneNumber").value("010-0000-0000"));
    }

    @Test
    void 수정_전후_고객정보를_반환한다() throws Exception {
        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType("application/json")
                        .content("""
                                {
                                  "address": "서울시 중구",
                                  "phoneNumber": "01012345678",
                                  "email": "new@hyundai.com",
                                  "name": "홍길동"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.before.phoneNumber").value("010-0000-0000"))
                .andExpect(jsonPath("$.after.phoneNumber").value("010-1234-5678"));
    }

    @Test
    void 잘못된_정렬값은_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/customers").param("sortBy", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 삭제_조건이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/customers"))
                .andExpect(status().isBadRequest());
    }
}
