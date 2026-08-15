package com.martflow.auth;

import com.martflow.security.Role;

import java.time.LocalDateTime;

/**
 * One logged-in session: a bearer token plus who it belongs to. Tokens are opaque secure-random
 * strings kept in memory (a restart logs everyone out — documented trade-off).
 */
public record AuthSession(String token, String userId, String username, String fullName,
                          Role role, LocalDateTime issuedAt, LocalDateTime lastSeen) {

    /** A fresh session snapshot with an updated last-seen timestamp (sliding TTL). */
    public AuthSession touched(LocalDateTime now) {
        return new AuthSession(token, userId, username, fullName, role, issuedAt, now);
    }
}
