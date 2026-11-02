package com.ikedi.pos.exception;

public class InvalidQuantityException extends PosException {

    private static final long serialVersionUID = 1L;

    public InvalidQuantityException(String message) {
        super(message);
    }
}
