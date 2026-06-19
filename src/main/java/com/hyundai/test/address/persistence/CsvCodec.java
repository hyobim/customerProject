package com.hyundai.test.address.persistence;

import com.hyundai.test.address.exception.CustomerDataFileException;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

final class CsvCodec {

    private CsvCodec() {
    }

    static List<String> readRecord(PushbackReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean started = false;

        int current;
        while ((current = reader.read()) != -1) {
            started = true;
            char character = (char) current;

            if (quoted) {
                if (character == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (character == '"' && field.isEmpty()) {
                quoted = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                fields.add(trimCarriageReturn(field));
                return fields;
            } else {
                field.append(character);
            }
        }

        if (quoted) {
            throw new CustomerDataFileException("닫히지 않은 CSV 따옴표가 있습니다.");
        }
        if (!started && field.isEmpty() && fields.isEmpty()) {
            return null;
        }
        fields.add(trimCarriageReturn(field));
        return fields;
    }

    static void writeRecord(Writer writer, List<String> fields) throws IOException {
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escape(fields.get(index)));
        }
        writer.write(System.lineSeparator());
    }

    private static String escape(String value) {
        if (value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String trimCarriageReturn(StringBuilder field) {
        int length = field.length();
        if (length > 0 && field.charAt(length - 1) == '\r') {
            return field.substring(0, length - 1);
        }
        return field.toString();
    }
}
