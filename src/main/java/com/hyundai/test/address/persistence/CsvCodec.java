package com.hyundai.test.address.persistence;

import com.hyundai.test.address.exception.CustomerDataFileException;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

final class CsvCodec {

    private enum State {
        FIELD_START,
        UNQUOTED,
        QUOTED,
        AFTER_QUOTE
    }

    private CsvCodec() {
    }

    static List<String> readRecord(PushbackReader reader) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.FIELD_START;
        boolean started = false;

        int current;
        while ((current = reader.read()) != -1) {
            started = true;
            char character = (char) current;

            switch (state) {
                case FIELD_START -> {
                    if (character == '"') {
                        state = State.QUOTED;
                    } else if (character == ',') {
                        fields.add("");
                    } else if (character == '\n') {
                        fields.add("");
                        return fields;
                    } else {
                        field.append(character);
                        state = State.UNQUOTED;
                    }
                }
                case UNQUOTED -> {
                    if (character == '"') {
                        throw new CustomerDataFileException(
                                "CSV 따옴표는 필드의 첫 문자에만 사용할 수 있습니다.");
                    } else if (character == ',') {
                        fields.add(field.toString());
                        field.setLength(0);
                        state = State.FIELD_START;
                    } else if (character == '\n') {
                        fields.add(trimCarriageReturn(field));
                        return fields;
                    } else {
                        field.append(character);
                    }
                }
                case QUOTED -> {
                    if (character == '"') {
                        state = State.AFTER_QUOTE;
                    } else {
                        field.append(character);
                    }
                }
                case AFTER_QUOTE -> {
                    if (character == '"') {
                        field.append('"');
                        state = State.QUOTED;
                    } else if (character == ',') {
                        fields.add(field.toString());
                        field.setLength(0);
                        state = State.FIELD_START;
                    } else if (character == '\n') {
                        fields.add(field.toString());
                        return fields;
                    } else if (character == '\r') {
                        int next = reader.read();
                        if (next == '\n') {
                            fields.add(field.toString());
                            return fields;
                        }
                        throw new CustomerDataFileException(
                                "CSV 닫는 따옴표 뒤에 허용되지 않은 문자가 있습니다.");
                    } else {
                        throw new CustomerDataFileException(
                                "CSV 닫는 따옴표 뒤에 허용되지 않은 문자가 있습니다.");
                    }
                }
            }
        }

        if (state == State.QUOTED) {
            throw new CustomerDataFileException("닫히지 않은 CSV 따옴표 필드가 있습니다.");
        }
        if (!started && field.isEmpty() && fields.isEmpty()) {
            return null;
        }
        fields.add(state == State.AFTER_QUOTE ? field.toString() : trimCarriageReturn(field));
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
