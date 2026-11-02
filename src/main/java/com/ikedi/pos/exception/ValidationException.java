package com.ikedi.pos.exception;

public class ValidationException extends PosException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
