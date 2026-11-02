package com.ikedi.pos.exception;

public class OutOfStockException extends PosException {

    private static final long serialVersionUID = 1L;

    public OutOfStockException(String message) {
        super(message);
    }
}
