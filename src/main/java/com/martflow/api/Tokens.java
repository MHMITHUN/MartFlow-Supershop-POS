package com.martflow.api;

/** Extracts the bearer token from an Authorization header value. */
final class Tokens {

    private Tokens() {
    }

    static String from(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing bearer token");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }
}
