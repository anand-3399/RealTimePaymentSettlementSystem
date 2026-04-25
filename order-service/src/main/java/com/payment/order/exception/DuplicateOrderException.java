package com.payment.order.exception;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String message) {
        super(message);
    }
}
