package com.martflow.security;

/**
 * Thrown when authentication fails (bad credentials, missing/expired token). Mapped to 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
