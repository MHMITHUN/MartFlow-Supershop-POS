package com.martflow.auth;

import com.martflow.common.TimeSource;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory bearer-token store with a sliding TTL: a session expires after 12 hours of
 * inactivity, and every lookup refreshes the clock. 256-bit secure-random tokens.
 */
public class TokenStore {

    private static final Duration TTL = Duration.ofHours(12);

    private final ConcurrentHashMap<String, AuthSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Issues a new session token for the given user. */
    public AuthSession issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        LocalDateTime now = TimeSource.now();
        AuthSession session = new AuthSession(token, user.getId(), user.getUsername(),
                user.getFullName(), user.getRole(), now, now);
        sessions.put(token, session);
        return session;
    }

    /** Resolves a token, sliding its expiry forward; empty when unknown or expired. */
    public Optional<AuthSession> lookup(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        AuthSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        LocalDateTime now = TimeSource.now();
        if (session.lastSeen().plus(TTL).isBefore(now)) {
            sessions.remove(token);
            return Optional.empty();
        }
        AuthSession touched = session.touched(now);
        sessions.put(token, touched);
        return Optional.of(touched);
    }

    /** Removes a session (logout). Silent for unknown tokens. */
    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Number of live sessions — used by tests and the admin view. */
    public int activeCount() {
        return sessions.size();
    }
}
