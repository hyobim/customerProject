package com.hyundai.test.address.controller;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerChange;
import com.hyundai.test.address.exception.CustomerNotFoundException;
import com.hyundai.test.address.exception.DuplicateCustomerException;
import com.hyundai.test.address.exception.InvalidSearchConditionException;
import com.hyundai.test.address.service.AddressBookService;
import com.hyundai.test.address.service.dto.CustomerSearchRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressBookController.class)
class AddressBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddressBookService service;

    @Test
    void 조회_파라미터를_요청_DTO로_바인딩하고_JSON_배열을_반환한다() throws Exception {
        Customer customer = customer();
        given(service.search(any(CustomerSearchRequest.class))).willReturn(List.of(customer));

        mockMvc.perform(get("/api/customers")
                        .param("phoneNumber", "01000000000")
                        .param("email", "hong@hyundai.com")
                        .param("address", "광진")
                        .param("name", "홍길")
                        .param("sortBy", "name")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phoneNumber").value("010-0000-0000"));

        ArgumentCaptor<CustomerSearchRequest> captor =
                ArgumentCaptor.forClass(CustomerSearchRequest.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue()).isEqualTo(CustomerSearchRequest.builder()
                .phoneNumber("01000000000")
                .email("hong@hyundai.com")
                .address("광진")
                .name("홍길")
                .sortBy("name")
                .direction("desc")
                .build());
    }

    @Test
    void 조회_결과가_없으면_빈_JSON_배열을_반환한다() throws Exception {
        given(service.search(any(CustomerSearchRequest.class))).willReturn(List.of());

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void 일부_조회_파라미터만_전달해도_나머지는_null로_바인딩한다() throws Exception {
        given(service.search(any(CustomerSearchRequest.class))).willReturn(List.of());

        mockMvc.perform(get("/api/customers").param("email", "hong@hyundai.com"))
                .andExpect(status().isOk());

        ArgumentCaptor<CustomerSearchRequest> captor =
                ArgumentCaptor.forClass(CustomerSearchRequest.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("hong@hyundai.com");
        assertThat(captor.getValue().phoneNumber()).isNull();
        assertThat(captor.getValue().sortBy()).isNull();
    }

    @Test
    void 수정_요청을_도메인으로_변환하고_수정_전후를_반환한다() throws Exception {
        Customer before = customer();
        Customer after = new Customer(
                "서울시 중구", "010-1234-5678", "new@hyundai.com", "홍길동");
        given(service.update(anyString(), any(Customer.class)))
                .willReturn(new CustomerChange(before, after));

        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType(MediaType.APPLICATION_JSON)
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

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(service).update(
                org.mockito.ArgumentMatchers.eq("01000000000"),
                customerCaptor.capture()
        );
        assertThat(customerCaptor.getValue()).isEqualTo(new Customer(
                "서울시 중구", "01012345678", "new@hyundai.com", "홍길동"));
    }

    @Test
    void 이메일_조건으로_삭제하고_삭제된_고객을_반환한다() throws Exception {
        given(service.delete(null, "hong@hyundai.com", null, null))
                .willReturn(List.of(customer()));

        mockMvc.perform(delete("/api/customers")
                        .param("email", "hong@hyundai.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("hong@hyundai.com"));
    }

    @Test
    void 주소_조건의_다건_삭제_결과를_배열로_반환한다() throws Exception {
        Customer second = new Customer(
                "서울시 중구", "010-1111-2222", "second@hyundai.com", "김고객");
        given(service.delete(null, null, "서울", null))
                .willReturn(List.of(customer(), second));

        mockMvc.perform(delete("/api/customers").param("address", "서울"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 수정_본문의_이메일_형식이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "address": "서울시 중구",
                                  "phoneNumber": "01012345678",
                                  "email": "invalid-email",
                                  "name": "홍길동"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("이메일은 아이디@도메인 형식이어야 합니다."));

        verifyNoInteractions(service);
    }

    @Test
    void 수정_본문이_JSON이_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("요청 본문의 JSON 형식이 올바르지 않습니다."));
    }

    @Test
    void 잘못된_검색_예외를_400으로_변환한다() throws Exception {
        given(service.search(any(CustomerSearchRequest.class)))
                .willThrow(new InvalidSearchConditionException(
                        "지원하지 않는 정렬 필드입니다: unknown"));

        mockMvc.perform(get("/api/customers").param("sortBy", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("지원하지 않는 정렬 필드입니다: unknown"));
    }

    @Test
    void 수정_대상_미존재_예외를_404로_변환한다() throws Exception {
        given(service.update(anyString(), any(Customer.class)))
                .willThrow(new CustomerNotFoundException(
                        "수정할 고객을 찾을 수 없습니다."));

        mockMvc.perform(put("/api/customers/01099999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("수정할 고객을 찾을 수 없습니다."));
    }

    @Test
    void 중복_예외를_409로_변환한다() throws Exception {
        given(service.update(anyString(), any(Customer.class)))
                .willThrow(new DuplicateCustomerException(
                        "이미 등록된 이메일입니다."));

        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("이미 등록된 이메일입니다."));
    }

    @Test
    void 예상하지_못한_예외는_안전한_메시지와_500을_반환한다() throws Exception {
        given(service.search(any(CustomerSearchRequest.class)))
                .willThrow(new IllegalStateException("내부 상세 오류"));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("요청을 처리하는 중 서버 오류가 발생했습니다."))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("내부 상세 오류"))));
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_405를_반환한다() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.path").value("/api/customers"));
    }

    @Test
    void 지원하지_않는_Content_Type은_415를_반환한다() throws Exception {
        mockMvc.perform(put("/api/customers/01000000000")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(validRequestBody()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.path").value("/api/customers/01000000000"));
    }

    @Test
    void 지원하지_않는_Accept은_406을_반환한다() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.error").value("Not Acceptable"))
                .andExpect(jsonPath("$.path").value("/api/customers"));
    }

    @Test
    void 빈_삭제_조건은_400_오류_JSON을_반환한다() throws Exception {
        given(service.delete("", null, null, null))
                .willThrow(new InvalidSearchConditionException(
                        AddressBookService.DELETE_CONDITION_MESSAGE));

        mockMvc.perform(delete("/api/customers").param("phoneNumber", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value(AddressBookService.DELETE_CONDITION_MESSAGE))
                .andExpect(jsonPath("$.path").value("/api/customers"));
    }

    @Test
    void 공백_삭제_조건은_400_오류_JSON을_반환한다() throws Exception {
        given(service.delete(null, "   ", null, null))
                .willThrow(new InvalidSearchConditionException(
                        AddressBookService.DELETE_CONDITION_MESSAGE));

        mockMvc.perform(delete("/api/customers").param("email", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(AddressBookService.DELETE_CONDITION_MESSAGE));
    }

    @Test
    void 빈_정렬_파라미터는_400_오류_JSON을_반환한다() throws Exception {
        given(service.search(any(CustomerSearchRequest.class)))
                .willThrow(new InvalidSearchConditionException(
                        AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE));

        for (String parameter : List.of("sortBy", "direction")) {
            for (String value : List.of("", "   ")) {
                mockMvc.perform(get("/api/customers").param(parameter, value))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.error").value("Bad Request"))
                        .andExpect(jsonPath("$.message")
                                .value(AddressBookService.EMPTY_SEARCH_CONDITION_MESSAGE))
                        .andExpect(jsonPath("$.path").value("/api/customers"))
                        .andExpect(content().string(
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.containsString("InvalidSearchConditionException"))));
            }
        }
    }

    private Customer customer() {
        return new Customer(
                "서울시 광진구",
                "010-0000-0000",
                "hong@hyundai.com",
                "홍길동"
        );
    }

    private String validRequestBody() {
        return """
                {
                  "address": "서울시 중구",
                  "phoneNumber": "01012345678",
                  "email": "new@hyundai.com",
                  "name": "홍길동"
                }
                """;
    }
}
