package com.hyundai.test.address.persistence;

import com.hyundai.test.address.exception.CustomerDataFileException;
import com.hyundai.test.address.repository.InMemoryCustomerRepository;
import com.hyundai.test.address.service.AddressBookService;
import com.hyundai.test.address.validation.CustomerValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCustomerStoreTest {

    @TempDir
    Path tempDirectory;

    @Test
    void 잘못된_행은_건너뛰고_후속_정상행을_적재한다() throws Exception {
        Path source = tempDirectory.resolve("address.csv");
        Files.writeString(source, """
                주소,연락처,이메일,이름
                서울,잘못된번호,bad@example.com,실패
                "서울, 광진구",01012345678,good@example.com,성공
                """, StandardCharsets.UTF_8);
        AddressBookService service = service();
        CsvCustomerStore store = new CsvCustomerStore(service, source.toString());

        CsvCustomerStore.LoadResult result = store.load();

        assertThat(result.loadedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(service.snapshot()).extracting("address").containsExactly("서울, 광진구");
    }

    @Test
    void CSV_문법_오류는_건너뛰지_않고_후속행_처리도_중단한다() throws Exception {
        Path source = tempDirectory.resolve("invalid-syntax.csv");
        Files.writeString(source, """
                주소,연락처,이메일,이름
                "서울"x,01012345678,bad@example.com,문법오류
                부산,01011112222,next@example.com,후속
                """, StandardCharsets.UTF_8);
        AddressBookService service = service();
        CsvCustomerStore store = new CsvCustomerStore(service, source.toString());

        assertThatThrownBy(store::load)
                .isInstanceOf(CustomerDataFileException.class)
                .hasMessageContaining("닫는 따옴표 뒤");
        assertThat(service.snapshot()).isEmpty();
    }

    @Test
    void 중복_행은_건너뛰고_후속_정상행을_적재한다() throws Exception {
        Path source = tempDirectory.resolve("duplicates.csv");
        Files.writeString(source, """
                주소,연락처,이메일,이름
                서울,01012345678,first@example.com,첫번째
                부산,01012345678,duplicate@example.com,중복
                대전,01011112222,next@example.com,후속
                """, StandardCharsets.UTF_8);
        AddressBookService service = service();
        CsvCustomerStore store = new CsvCustomerStore(service, source.toString());

        assertThat(store.load()).isEqualTo(new CsvCustomerStore.LoadResult(2, 1));
        assertThat(service.snapshot())
                .extracting("email")
                .containsExactlyInAnyOrder("first@example.com", "next@example.com");
    }

    @Test
    void 저장할_때_원본을_백업하고_메모리_스냅샷을_반영한다() throws Exception {
        Path source = tempDirectory.resolve("default_address.csv");
        Files.writeString(source, "주소,연락처,이메일,이름\n", StandardCharsets.UTF_8);
        AddressBookService service = service();
        service.add(new com.hyundai.test.address.domain.Customer(
                "서울", "01012345678", "user@example.com", "홍길동"));
        CsvCustomerStore store = new CsvCustomerStore(service, source.toString());

        Path backup = store.save();

        assertThat(backup).exists();
        assertThat(Files.readString(source)).contains("010-1234-5678");
        assertThat(Files.readString(backup)).isEqualTo("주소,연락처,이메일,이름\n");
    }

    @Test
    void 임시_파일_쓰기가_실패하면_원본을_보존하고_임시_파일을_정리한다() throws Exception {
        Path source = tempDirectory.resolve("default_address.csv");
        String original = "주소,연락처,이메일,이름\n서울,01012345678,old@example.com,기존\n";
        Files.writeString(source, original, StandardCharsets.UTF_8);
        CsvCustomerStore store = new CsvCustomerStore(service(), source.toString()) {
            @Override
            void writeSnapshot(Path target) throws IOException {
                throw new IOException("의도한 쓰기 실패");
            }
        };

        assertThatThrownBy(store::save)
                .isInstanceOf(CustomerDataFileException.class)
                .hasCauseInstanceOf(IOException.class)
                .rootCause()
                .hasMessage("의도한 쓰기 실패");

        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(temporaryFiles()).isEmpty();
    }

    @Test
    void 원본_교체가_실패하면_백업으로_원본을_복구하고_임시_파일을_정리한다() throws Exception {
        Path source = tempDirectory.resolve("default_address.csv");
        String original = "주소,연락처,이메일,이름\n서울,01012345678,old@example.com,기존\n";
        Files.writeString(source, original, StandardCharsets.UTF_8);
        AddressBookService service = service();
        service.add(new com.hyundai.test.address.domain.Customer(
                "부산", "01099999999", "new@example.com", "신규"));
        CsvCustomerStore store = new CsvCustomerStore(service, source.toString()) {
            @Override
            void replaceSource(Path temporary) throws IOException {
                Files.writeString(source, "손상된 원본", StandardCharsets.UTF_8);
                throw new IOException("의도한 교체 실패");
            }
        };

        assertThatThrownBy(store::save)
                .isInstanceOf(CustomerDataFileException.class)
                .hasCauseInstanceOf(IOException.class)
                .rootCause()
                .hasMessage("의도한 교체 실패");

        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo(original);
        assertThat(temporaryFiles()).isEmpty();
    }

    @Test
    void 원본_파일이_없으면_저장에_실패한다() {
        Path missing = tempDirectory.resolve("missing.csv");
        CsvCustomerStore store = new CsvCustomerStore(service(), missing.toString());

        assertThatThrownBy(store::save)
                .isInstanceOf(CustomerDataFileException.class)
                .hasMessageContaining("없거나 읽을 수 없습니다");
    }

    @Test
    void 원본_파일이_없으면_로딩에_실패한다() {
        Path missing = tempDirectory.resolve("missing.csv");
        CsvCustomerStore store = new CsvCustomerStore(service(), missing.toString());

        assertThatThrownBy(store::load)
                .isInstanceOf(CustomerDataFileException.class)
                .hasMessageContaining("없거나 읽을 수 없습니다");
    }

    @Test
    void 헤더가_잘못되면_로딩에_실패한다() throws Exception {
        Path source = tempDirectory.resolve("invalid-header.csv");
        Files.writeString(
                source,
                "이름,주소,연락처,이메일\n",
                StandardCharsets.UTF_8
        );
        CsvCustomerStore store = new CsvCustomerStore(service(), source.toString());

        assertThatThrownBy(store::load)
                .isInstanceOf(CustomerDataFileException.class)
                .hasMessageContaining("CSV 헤더");
    }

    private AddressBookService service() {
        return new AddressBookService(
                new InMemoryCustomerRepository(),
                new CustomerValidator()
        );
    }

    private List<Path> temporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(tempDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
        }
    }
}
