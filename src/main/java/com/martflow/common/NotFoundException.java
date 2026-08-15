package com.martflow.common;

/**
 * Thrown when a referenced entity does not exist. Mapped to HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
