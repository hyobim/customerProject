package com.hyundai.test.address.persistence;

import com.hyundai.test.address.domain.Customer;
import com.hyundai.test.address.exception.CustomerDataFileException;
import com.hyundai.test.address.exception.DuplicateCustomerException;
import com.hyundai.test.address.exception.InvalidCustomerException;
import com.hyundai.test.address.service.AddressBookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvCustomerStore {

    private static final Logger log = LoggerFactory.getLogger(CsvCustomerStore.class);
    private static final List<String> HEADER = List.of("주소", "연락처", "이메일", "이름");
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AddressBookService service;
    private final Path sourcePath;

    public CsvCustomerStore(
            AddressBookService service,
            @Value("${address.csv.path:default_address.csv}") String sourcePath
    ) {
        this.service = service;
        this.sourcePath = Path.of(sourcePath).toAbsolutePath().normalize();
    }

    public LoadResult load() {
        ensureReadableSource();
        try {
            if (Files.size(sourcePath) == 0) {
                log.warn("주소록 CSV 파일이 비어 있습니다: {}", sourcePath);
                return new LoadResult(0, 0);
            }

            try (BufferedReader bufferedReader = Files.newBufferedReader(
                    sourcePath, StandardCharsets.UTF_8);
                 PushbackReader reader = new PushbackReader(bufferedReader, 1)) {
                List<String> header = removeUtf8Bom(CsvCodec.readRecord(reader));
                validateHeader(header);

                int loaded = 0;
                int skipped = 0;
                int rowNumber = 1;
                List<String> row;
                while ((row = CsvCodec.readRecord(reader)) != null) {
                    rowNumber++;
                    if (isBlankRow(row)) {
                        continue;
                    }
                    if (row.size() != HEADER.size()) {
                        skipped++;
                        log.warn("CSV {}행을 건너뜁니다: 열 개수가 {}개입니다.",
                                rowNumber, row.size());
                        continue;
                    }
                    try {
                        service.add(new Customer(row.get(0), row.get(1), row.get(2), row.get(3)));
                        loaded++;
                    } catch (InvalidCustomerException | DuplicateCustomerException exception) {
                        skipped++;
                        log.warn("CSV {}행을 건너뜁니다: {}", rowNumber, exception.getMessage());
                    }
                }
                log.info("주소록 CSV 적재 완료: 성공 {}건, 건너뜀 {}건", loaded, skipped);
                return new LoadResult(loaded, skipped);
            }
        } catch (IOException exception) {
            throw new CustomerDataFileException("주소록 CSV 파일을 읽을 수 없습니다.", exception);
        }
    }

    public Path save() {
        ensureReadableSource();
        Path parent = sourcePath.getParent();
        String fileName = sourcePath.getFileName().toString();
        String baseName = fileName.toLowerCase().endsWith(".csv")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        Path backup = parent.resolve(
                baseName + "_" + LocalDateTime.now().format(BACKUP_TIMESTAMP) + ".bak.csv");

        try {
            Files.copy(sourcePath, backup, StandardCopyOption.REPLACE_EXISTING);
            Path temporary = Files.createTempFile(parent, baseName + "_", ".tmp");
            try {
                writeSnapshot(temporary);
                try {
                    replaceSource(temporary);
                } catch (IOException replaceException) {
                    restoreSource(backup, replaceException);
                    throw replaceException;
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            log.info("주소록 CSV 저장 완료: 원본 {}, 백업 {}", sourcePath, backup);
            return backup;
        } catch (IOException exception) {
            throw new CustomerDataFileException(
                    "주소록 CSV 저장에 실패했습니다. 기존 원본은 보존됩니다.", exception);
        }
    }

    public Path sourcePath() {
        return sourcePath;
    }

    void writeSnapshot(Path target) throws IOException {
        List<Customer> customers = service.snapshot().stream()
                .sorted(java.util.Comparator.comparing(Customer::phoneNumber))
                .toList();
        try (BufferedWriter writer = Files.newBufferedWriter(
                target, StandardCharsets.UTF_8)) {
            CsvCodec.writeRecord(writer, HEADER);
            for (Customer customer : customers) {
                CsvCodec.writeRecord(writer, List.of(
                        customer.address(),
                        customer.phoneNumber(),
                        customer.email(),
                        customer.name()
                ));
            }
        }
    }

    void replaceSource(Path temporary) throws IOException {
        try {
            Files.move(
                    temporary,
                    sourcePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, sourcePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void restoreSource(Path backup, IOException replaceException) {
        try {
            Files.copy(backup, sourcePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException restoreException) {
            replaceException.addSuppressed(restoreException);
        }
    }

    private void ensureReadableSource() {
        if (!Files.isRegularFile(sourcePath) || !Files.isReadable(sourcePath)) {
            throw new CustomerDataFileException(
                    "주소록 CSV 원본 파일이 없거나 읽을 수 없습니다: " + sourcePath);
        }
    }

    private void validateHeader(List<String> header) {
        if (!HEADER.equals(header)) {
            throw new CustomerDataFileException(
                    "CSV 헤더는 주소,연락처,이메일,이름 순서여야 합니다.");
        }
    }

    private List<String> removeUtf8Bom(List<String> header) {
        if (header == null || header.isEmpty() || !header.get(0).startsWith("\uFEFF")) {
            return header;
        }
        List<String> normalized = new ArrayList<>(header);
        normalized.set(0, normalized.get(0).substring(1));
        return normalized;
    }

    private boolean isBlankRow(List<String> row) {
        return row.size() == 1 && row.get(0).isBlank();
    }

    public record LoadResult(int loadedCount, int skippedCount) {
    }
}
