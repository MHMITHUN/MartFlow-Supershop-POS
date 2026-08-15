package com.martflow.billing;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Login-token &rarr; live billing session. Idle sessions are swept after 4 hours so a stale
 * till left open cannot hold memory (and a returning cashier simply starts fresh).
 */
public class BillingSessionRegistry {

    private static final long IDLE_MS = 4L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, BillingSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Millisecond source — injected so tests can fast-forward. */
    public interface Clock {
        long nowMs();
    }

    public BillingSessionRegistry(Clock clock) {
        this.clock = clock;
    }

    public BillingSession sessionFor(String token) {
        sweepIdle();
        long now = clock.nowMs();
        return sessions.compute(token, (t, session) -> {
            if (session == null) {
                return new BillingSession(now);
            }
            session.touch(now);
            return session;
        });
    }

    public int activeSessions() {
        return sessions.size();
    }

    private void sweepIdle() {
        long now = clock.nowMs();
        sessions.entrySet().removeIf(entry ->
                now - entry.getValue().lastAccessMs() > IDLE_MS);
    }
}
