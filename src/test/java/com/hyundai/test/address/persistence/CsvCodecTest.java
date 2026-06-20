package com.hyundai.test.address.persistence;

import com.hyundai.test.address.exception.CustomerDataFileException;
import com.hyundai.test.address.repository.InMemoryCustomerRepository;
import com.hyundai.test.address.service.AddressBookService;
import com.hyundai.test.address.validation.CustomerValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PushbackReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCodecTest {

    @TempDir
    Path tempDirectory;

    @Test
    void UTF8_BOM이_있는_CSV를_적재한다() throws Exception {
        Path source = tempDirectory.resolve("bom.csv");
        Files.writeString(
                source,
                "\uFEFF주소,연락처,이메일,이름\n서울,01012345678,user@example.com,홍길동\n",
                StandardCharsets.UTF_8
        );
        CsvCustomerStore store = new CsvCustomerStore(service(), source.toString());

        assertThat(store.load()).isEqualTo(new CsvCustomerStore.LoadResult(1, 0));
    }

    @Test
    void 쉼표_이중따옴표_필드내_개행을_하나의_레코드로_읽는다() throws Exception {
        List<String> record = read("\"서울, 광진구\",\"홍\"\"길동\",\"두 줄\n메모\",\n");

        assertThat(record).containsExactly("서울, 광진구", "홍\"길동", "두 줄\n메모", "");
    }

    @Test
    void CRLF와_LF를_모두_레코드_경계로_처리한다() throws Exception {
        try (PushbackReader reader = reader("a,b\r\nc,d\n")) {
            assertThat(CsvCodec.readRecord(reader)).containsExactly("a", "b");
            assertThat(CsvCodec.readRecord(reader)).containsExactly("c", "d");
            assertThat(CsvCodec.readRecord(reader)).isNull();
        }
    }

    @Test
    void 닫히지_않은_따옴표는_명확한_예외를_발생시킨다() {
        assertThatThrownBy(() -> read("\"닫히지 않음"))
                .isInstanceOf(CustomerDataFileException.class)
                .hasMessageContaining("닫히지 않은");
    }

    @Test
    void 열_개수가_부족하거나_초과한_행은_건너뛴다() throws Exception {
        Path source = tempDirectory.resolve("columns.csv");
        Files.writeString(source, """
                주소,연락처,이메일,이름
                서울,01012345678,short@example.com
                부산,01011112222,valid@example.com,정상
                대전,01033334444,long@example.com,초과,필드
                """, StandardCharsets.UTF_8);
        CsvCustomerStore store = new CsvCustomerStore(service(), source.toString());

        assertThat(store.load()).isEqualTo(new CsvCustomerStore.LoadResult(1, 2));
    }

    @Test
    void 매우_긴_필드도_손실없이_왕복한다() throws Exception {
        String longField = "가".repeat(100_000);
        StringWriter writer = new StringWriter();

        CsvCodec.writeRecord(writer, List.of(longField, "끝"));

        assertThat(read(writer.toString())).containsExactly(longField, "끝");
    }

    private List<String> read(String csv) throws Exception {
        try (PushbackReader reader = reader(csv)) {
            return CsvCodec.readRecord(reader);
        }
    }

    private PushbackReader reader(String csv) {
        return new PushbackReader(new StringReader(csv), 1);
    }

    private AddressBookService service() {
        return new AddressBookService(
                new InMemoryCustomerRepository(),
                new CustomerValidator()
        );
    }
}
