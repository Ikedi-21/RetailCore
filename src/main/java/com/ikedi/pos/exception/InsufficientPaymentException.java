package com.ikedi.pos.exception;

public class InsufficientPaymentException extends PosException {

    private static final long serialVersionUID = 1L;

    public InsufficientPaymentException(String message) {
        super(message);
    }
}
