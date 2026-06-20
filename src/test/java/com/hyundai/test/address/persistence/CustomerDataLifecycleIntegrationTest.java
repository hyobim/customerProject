package com.hyundai.test.address.persistence;

import com.hyundai.test.address.Application;
import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.repository.InMemoryCustomerRepository;
import com.hyundai.test.address.service.AddressBookService;
import com.hyundai.test.address.validation.CustomerValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerDataLifecycleIntegrationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void Spring_Context_정상_종료가_원본을_백업하고_최종_메모리_상태를_저장한다() throws Exception {
        Path source = tempDirectory.resolve("lifecycle-address.csv");
        String original = """
                주소,연락처,이메일,이름
                서울,01011112222,old@example.com,기존고객
                부산,01033334444,delete@example.com,삭제고객
                """;
        Files.writeString(source, original, StandardCharsets.UTF_8);

        ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "address.csv.path=" + source
                )
                .run();
        try {
            AddressBookService service = context.getBean(AddressBookService.class);
            assertThat(service.snapshot()).hasSize(2);

            service.update(
                    "01011112222",
                    new Customer("대전", "01055556666", "updated@example.com", "수정고객")
            );
            service.delete(null, "delete@example.com", null, null);
        } finally {
            context.close();
        }

        List<Path> backups;
        try (Stream<Path> files = Files.list(tempDirectory)) {
            backups = files
                    .filter(path -> path.getFileName().toString().endsWith(".bak.csv"))
                    .toList();
        }
        assertThat(backups).hasSize(1);
        assertThat(Files.readString(backups.get(0), StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(Files.readString(source, StandardCharsets.UTF_8))
                .contains("대전,010-5555-6666,updated@example.com,수정고객")
                .doesNotContain("delete@example.com");

        AddressBookService reloadedService = service();
        CsvCustomerStore reloadedStore = new CsvCustomerStore(reloadedService, source.toString());
        assertThat(reloadedStore.load()).isEqualTo(new CsvCustomerStore.LoadResult(1, 0));
        assertThat(reloadedService.snapshot())
                .extracting(Customer::phoneNumber, Customer::email)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "010-5555-6666", "updated@example.com"));
    }

    private AddressBookService service() {
        return new AddressBookService(
                new InMemoryCustomerRepository(),
                new CustomerValidator()
        );
    }
}
