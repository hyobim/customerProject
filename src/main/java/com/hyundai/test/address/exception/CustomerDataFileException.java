package com.hyundai.test.address.exception;

public class CustomerDataFileException extends RuntimeException {
    public CustomerDataFileException(String message) {
        super(message);
    }

    public CustomerDataFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
