package com.hyundai.test.address.repository;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerSearchCondition;
import com.hyundai.test.address.domain.SortDirection;
import com.hyundai.test.address.domain.SortField;
import com.hyundai.test.address.exception.DuplicateCustomerException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryCustomerRepositoryTest {

    @Test
    void 이메일_보조_인덱스로_정확_조회한다() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        Customer customer = customer("010-1234-5678", "user@example.com");
        repository.add(customer);

        var result = repository.search(new CustomerSearchCondition(
                null, "user@example.com", null, null, SortField.PHONE_NUMBER, SortDirection.ASC));

        assertThat(result).containsExactly(customer);
    }

    @Test
    void 수정하면_이전_이메일_인덱스를_제거한다() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.add(customer("010-1234-5678", "old@example.com"));

        repository.update(
                "010-1234-5678",
                customer("010-9999-9999", "new@example.com")
        );

        assertThat(repository.search(new CustomerSearchCondition(
                null, "old@example.com", null, null, null, null))).isEmpty();
    }

    @Test
    void 중복_이메일을_거부한다() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.add(customer("010-1234-5678", "same@example.com"));

        assertThatThrownBy(() ->
                repository.add(customer("010-9999-9999", "same@example.com")))
                .isInstanceOf(DuplicateCustomerException.class);
    }

    @Test
    void 동시_중복_등록은_한건만_성공한다() throws Exception {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();

        for (int index = 0; index < 2; index++) {
            executor.submit(() -> {
                try {
                    start.await();
                    repository.add(customer("010-1234-5678", "same@example.com"));
                    success.incrementAndGet();
                } catch (DuplicateCustomerException ignored) {
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        assertThat(success).hasValue(1);
    }

    private Customer customer(String phoneNumber, String email) {
        return new Customer("서울", phoneNumber, email, "홍길동");
    }
}
