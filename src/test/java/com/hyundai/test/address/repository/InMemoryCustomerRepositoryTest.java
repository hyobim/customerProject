package com.hyundai.test.address.repository;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.domain.CustomerSearchCondition;
import com.hyundai.test.address.domain.SortDirection;
import com.hyundai.test.address.domain.SortField;
import com.hyundai.test.address.exception.CustomerNotFoundException;
import com.hyundai.test.address.exception.DuplicateCustomerException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    @Test
    void 동일_고객을_동시에_수정해도_인덱스가_일관된다() throws Exception {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.add(customer("010-1234-5678", "original@example.com"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger success = new AtomicInteger();

        List<Future<?>> futures = List.of(
                executor.submit(() -> updateAfterStart(
                        repository, start, customer("010-1111-1111", "first@example.com"), success)),
                executor.submit(() -> updateAfterStart(
                        repository, start, customer("010-2222-2222", "second@example.com"), success))
        );
        start.countDown();
        for (Future<?> future : futures) {
            future.get(3, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(success).hasValue(1);
        assertRepositoryConsistent(repository);
        assertThat(repository.search(condition(null, "original@example.com"))).isEmpty();
    }

    @Test
    void 수정과_삭제가_경쟁해도_최종_인덱스가_일관된다() throws Exception {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.add(customer("010-1234-5678", "original@example.com"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> update = executor.submit(() -> {
            await(start);
            try {
                repository.update(
                        "010-1234-5678",
                        customer("010-9999-9999", "updated@example.com")
                );
            } catch (CustomerNotFoundException ignored) {
            }
        });
        Future<?> delete = executor.submit(() -> {
            await(start);
            try {
                repository.delete(condition("010-1234-5678", null));
            } catch (CustomerNotFoundException ignored) {
            }
        });

        start.countDown();
        update.get(3, TimeUnit.SECONDS);
        delete.get(3, TimeUnit.SECONDS);
        executor.shutdown();

        assertRepositoryConsistent(repository);
        assertThat(repository.snapshot()).hasSizeLessThanOrEqualTo(1);
        assertThat(repository.search(condition(null, "original@example.com"))).isEmpty();
    }

    @Test
    void 스냅샷과_수정이_동시에_실행돼도_중간_상태가_노출되지_않는다() throws Exception {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.add(customer("010-1234-5678", "first@example.com"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> updates = executor.submit(() -> {
            await(start);
            String currentPhone = "010-1234-5678";
            for (int index = 0; index < 100; index++) {
                Customer replacement = index % 2 == 0
                        ? customer("010-9999-9999", "second@example.com")
                        : customer("010-1234-5678", "first@example.com");
                repository.update(currentPhone, replacement);
                currentPhone = replacement.phoneNumber();
            }
        });
        Future<?> snapshots = executor.submit(() -> {
            await(start);
            for (int index = 0; index < 100; index++) {
                List<Customer> snapshot = repository.snapshot();
                assertThat(snapshot).hasSize(1);
                assertThat(snapshot.get(0)).isIn(
                        customer("010-1234-5678", "first@example.com"),
                        customer("010-9999-9999", "second@example.com")
                );
            }
        });

        start.countDown();
        updates.get(3, TimeUnit.SECONDS);
        snapshots.get(3, TimeUnit.SECONDS);
        executor.shutdown();

        assertRepositoryConsistent(repository);
    }

    private void updateAfterStart(
            InMemoryCustomerRepository repository,
            CountDownLatch start,
            Customer replacement,
            AtomicInteger success
    ) {
        await(start);
        try {
            repository.update("010-1234-5678", replacement);
            success.incrementAndGet();
        } catch (CustomerNotFoundException ignored) {
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void assertRepositoryConsistent(InMemoryCustomerRepository repository) {
        List<Customer> snapshot = repository.snapshot();
        assertThat(snapshot).extracting(Customer::phoneNumber).doesNotHaveDuplicates();
        assertThat(snapshot).extracting(Customer::email).doesNotHaveDuplicates();
        for (Customer customer : snapshot) {
            assertThat(repository.search(condition(customer.phoneNumber(), null)))
                    .containsExactly(customer);
            assertThat(repository.search(condition(null, customer.email())))
                    .containsExactly(customer);
        }
    }

    private CustomerSearchCondition condition(String phoneNumber, String email) {
        return new CustomerSearchCondition(
                phoneNumber, email, null, null, SortField.PHONE_NUMBER, SortDirection.ASC);
    }

    private Customer customer(String phoneNumber, String email) {
        return new Customer("서울", phoneNumber, email, "홍길동");
    }
}
