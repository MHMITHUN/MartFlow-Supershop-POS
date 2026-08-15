package com.martflow.auth;

import com.martflow.common.TimeSource;
import com.martflow.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bearer tokens: issue, sliding TTL, revocation. */
class TokenStoreTest {

    private final TokenStore tokens = new TokenStore();

    @AfterEach
    void resetClock() {
        TimeSource.resetToSystemClock();
    }

    private User user() {
        return new User("u-1", "cashier", "Till Operator", "hash", Role.CASHIER, true,
                LocalDateTime.of(2026, 1, 1, 9, 0));
    }

    private void freezeAt(String instant) {
        TimeSource.useFixedClock(Clock.fixed(Instant.parse(instant), TimeSource.ZONE));
    }

    @Test
    void issuesAndResolvesSessions() {
        freezeAt("2026-08-15T04:00:00Z");
        AuthSession session = tokens.issue(user());
        assertEquals("cashier", session.username());
        assertEquals(Role.CASHIER, session.role());
        assertTrue(tokens.lookup(session.token()).isPresent());
    }

    @Test
    void slidingTtlKeepsActiveSessionsAlive() {
        freezeAt("2026-08-15T04:00:00Z");
        AuthSession session = tokens.issue(user());
        // 11 hours later -> still valid, and the TTL slides forward from THIS lookup
        freezeAt("2026-08-15T15:00:00Z");
        assertTrue(tokens.lookup(session.token()).isPresent());
        // another 11 hours (22h total, but only 11h since last touch) -> still valid
        freezeAt("2026-08-16T02:00:00Z");
        assertTrue(tokens.lookup(session.token()).isPresent());
    }

    @Test
    void idleSessionsExpire() {
        freezeAt("2026-08-15T04:00:00Z");
        AuthSession session = tokens.issue(user());
        // 13 hours of silence -> expired
        freezeAt("2026-08-15T17:00:00Z");
        assertFalse(tokens.lookup(session.token()).isPresent());
        // and stays gone afterwards
        assertFalse(tokens.lookup(session.token()).isPresent());
    }

    @Test
    void revokeLogsOutImmediately() {
        freezeAt("2026-08-15T04:00:00Z");
        AuthSession session = tokens.issue(user());
        tokens.revoke(session.token());
        assertEquals(Optional.empty(), tokens.lookup(session.token()));
        assertEquals(0, tokens.activeCount());
    }

    @Test
    void blankTokensResolveToNothing() {
        assertFalse(tokens.lookup(null).isPresent());
        assertFalse(tokens.lookup("  ").isPresent());
        assertFalse(tokens.lookup("deadbeef").isPresent());
    }
}
