package com.ikedi.pos.exception;

public class ProductNotFoundException extends PosException {

    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(String message) {
        super(message);
    }
}
