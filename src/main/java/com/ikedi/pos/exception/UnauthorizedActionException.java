package com.ikedi.pos.exception;

public class UnauthorizedActionException extends PosException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedActionException(String message) {
        super(message);
    }
}
