package com.rtps.bank1.exception;

public class BusinessValidationException extends RuntimeException {

    private static final long serialVersionUID = 112345678L;

	public BusinessValidationException(String message) {
        super(message);
    }
}
