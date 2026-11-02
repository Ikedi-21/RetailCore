package com.ikedi.pos.exception;

public class ExpiredProductException extends PosException {

    private static final long serialVersionUID = 1L;

    public ExpiredProductException(String message) {
        super(message);
    }
}
