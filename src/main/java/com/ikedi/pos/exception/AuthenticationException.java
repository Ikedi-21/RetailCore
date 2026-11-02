package com.ikedi.pos.exception;

public class AuthenticationException extends PosException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }
}
