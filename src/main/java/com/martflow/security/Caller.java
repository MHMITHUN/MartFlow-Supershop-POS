package com.martflow.security;

/**
 * The authenticated staff member behind the current request. Populated by the auth filter from the
 * bearer token and read anywhere via {@link RoleContext#current()}.
 */
public record Caller(String userId, String username, Role role) {

    public Caller {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Caller needs a userId");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Caller needs a username");
        }
        if (role == null) {
            throw new IllegalArgumentException("Caller needs a role");
        }
    }

    public boolean isAtLeast(Role required) {
        return role.atLeast(required);
    }
}
