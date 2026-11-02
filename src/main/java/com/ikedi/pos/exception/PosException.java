package com.ikedi.pos.exception;

/**
 * Base class for expected, user-facing failures. The CLI catches this type
 * only, so programming errors are never hidden behind a friendly message.
 */
public class PosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PosException(String message) {
        super(message);
    }
}
