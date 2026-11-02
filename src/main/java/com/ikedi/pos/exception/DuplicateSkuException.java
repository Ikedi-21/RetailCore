package com.ikedi.pos.exception;

public class DuplicateSkuException extends PosException {

    private static final long serialVersionUID = 1L;

    public DuplicateSkuException(String message) {
        super(message);
    }
}
