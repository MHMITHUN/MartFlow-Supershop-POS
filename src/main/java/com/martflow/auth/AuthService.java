package com.martflow.auth;

import com.martflow.common.NotFoundException;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import com.martflow.security.UnauthorizedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Login/logout and staff-account administration. Bad usernames and bad passwords produce the
 * same error so the endpoint cannot be used to enumerate accounts. Account changes are
 * admin-only.
 */
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenStore tokens;

    public AuthService(UserRepository users, PasswordHasher hasher, TokenStore tokens) {
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    /** Verifies credentials and issues a bearer session. */
    public AuthSession login(String username, String password) {
        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty()) {
            // burn the same work as a real verify so timing cannot reveal valid usernames
            hasher.verify(password, "AA==:AA==");
            throw new UnauthorizedException("Invalid username or password");
        }
        User user = found.get();
        if (!user.isActive()) {
            throw new UnauthorizedException("This account is disabled");
        }
        if (!hasher.verify(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }
        return tokens.issue(user);
    }

    /** Resolves a bearer token to its session (refreshing the sliding TTL). */
    public Optional<AuthSession> resolve(String token) {
        return tokens.lookup(token);
    }

    /** Logs out the current session. */
    public void logout(String token) {
        tokens.revoke(token);
    }

    public List<User> listUsers() {
        RoleGate.requireAtLeast(Role.ADMIN);
        return users.findAll();
    }

    /** Creates a staff account (ADMIN only). Passwords are hashed before storage. */
    public User createUser(String username, String password, String fullName, Role role) {
        RoleGate.requireAtLeast(Role.ADMIN);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (users.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        User user = new User("u-" + UUID.randomUUID().toString().substring(0, 8),
                username.trim(), fullName == null ? username.trim() : fullName,
                hasher.hash(password), role == null ? Role.CASHIER : role, true, null);
        return users.save(user);
    }

    /** Updates a staff account (ADMIN only): name, role, active flag, optional new password. */
    public User updateUser(String id, String fullName, Role role, Boolean active, String newPassword) {
        RoleGate.requireAtLeast(Role.ADMIN);
        User user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("Unknown user: " + id));
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (role != null) {
            user.setRole(role);
        }
        if (active != null) {
            user.setActive(active);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPasswordHash(hasher.hash(newPassword));
        }
        return users.save(user);
    }
}
