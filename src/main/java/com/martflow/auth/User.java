package com.martflow.auth;

import com.martflow.common.TimeSource;
import com.martflow.security.Role;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A staff account: the owner (ADMIN), floor managers (MANAGER) and till operators (CASHIER).
 * Passwords are stored only as PBKDF2 hashes — never plaintext.
 */
public class User {

    private final String id;
    private final String username;
    private String fullName;
    private String passwordHash;
    private Role role;
    private boolean active;
    private final LocalDateTime createdAt;

    public User(String id, String username, String fullName, String passwordHash,
                Role role, boolean active, LocalDateTime createdAt) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.active = active;
        this.createdAt = createdAt == null ? TimeSource.now() : createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Replaces the password hash — pass through {@code PasswordHasher.hash} first. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
