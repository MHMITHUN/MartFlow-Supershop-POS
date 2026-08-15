package com.martflow.security;

/**
 * Thrown when the current caller's role is insufficient for an operation. Mapped to HTTP 403.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
