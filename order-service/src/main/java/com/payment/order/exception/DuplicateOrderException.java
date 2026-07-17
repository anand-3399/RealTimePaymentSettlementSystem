package com.payment.order.exception;

public class DuplicateOrderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

	public DuplicateOrderException(String message) {
        super(message);
    }
}
