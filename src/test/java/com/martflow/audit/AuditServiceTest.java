package com.martflow.audit;

import com.martflow.common.TimeSource;
import com.martflow.persistence.InMemoryAuditLogRepository;
import com.martflow.security.Caller;
import com.martflow.security.Role;
import com.martflow.security.RoleContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The activity trail's own rules: actor captured from the caller, newest-first query filters,
 * and the 500-entry cap trimming the oldest rows.
 */
class AuditServiceTest {

    private static AuditService audit;
    private static InMemoryAuditLogRepository repo;

    @BeforeAll
    static void init() {
        repo = new InMemoryAuditLogRepository();
        audit = new AuditService(repo);
        // stepping clock: every read of "now" advances 1 ms, so each record gets a distinct
        // timestamp (deterministic newest-first / oldest-first) while "today" stays 2026-08-16
        TimeSource.useFixedClock(new SteppingClock());
    }

    /** Fixed-base clock that advances 1 ms per instant() read — deterministic yet strictly ordered. */
    private static final class SteppingClock extends Clock {

        private Instant current = Instant.parse("2026-08-16T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Dhaka");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public synchronized Instant instant() {
            Instant now = current;
            current = current.plusMillis(1);
            return now;
        }
    }

    @AfterAll
    static void tearDown() {
        TimeSource.resetToSystemClock();
    }

    @AfterEach
    void clearCaller() {
        RoleContext.clear();
        repo.findAll().forEach(e -> repo.delete(e.getId()));
    }

    @Test
    void recordCapturesCallerActorAndRole() {
        RoleContext.set(new Caller("u-manager", "manager", Role.MANAGER));
        audit.record(AuditLog.Action.SALE_VOIDED, "SALE", "MF-1", "reason: test");
        AuditLog entry = audit.query(null, null, null, AuditLog.Action.SALE_VOIDED, 10).get(0);
        assertEquals("manager", entry.getActorUsername());
        assertEquals("MANAGER", entry.getRole());
        assertEquals("MF-1", entry.getTargetId());
        assertTrue(entry.getDetail().contains("test"));
    }

    @Test
    void recordAttemptWritesTypedUsernameWithoutCaller() {
        audit.recordAttempt("intruder", AuditLog.Action.LOGIN_FAILED, "bad password");
        AuditLog entry = audit.query(null, null, "intruder", AuditLog.Action.LOGIN_FAILED, 10).get(0);
        assertEquals("intruder", entry.getActorUsername());
        assertEquals("-", entry.getRole());
        assertEquals("AUTH", entry.getTargetType());
    }

    @Test
    void queryFiltersByWindowActorAndActionNewestFirst() {
        // pinned clock: "today" is 2026-08-16 (Dhaka) for every entry
        RoleContext.set(new Caller("u-manager", "manager", Role.MANAGER));
        audit.record(AuditLog.Action.PO_SUBMITTED, "PO", "PO-1", "first");
        audit.record(AuditLog.Action.PO_RECEIVED, "PO", "PO-1", "second");
        audit.record(AuditLog.Action.PO_CLOSED, "PO", "PO-1", "third");

        assertEquals(3, audit.query(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 16), null, null, 200).size());
        assertEquals(0, audit.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null, 200).size(),
                "entries outside the window must not match");
        assertEquals(1, audit.query(null, null, null, AuditLog.Action.PO_RECEIVED, 200).size());
        assertEquals(0, audit.query(null, null, "someone-else", null, 200).size());

        var newestFirst = audit.query(null, null, null, null, 2);
        assertEquals("third", newestFirst.get(0).getDetail()); // newest first
        assertFalse(newestFirst.stream().anyMatch(e -> "first".equals(e.getDetail())),
                "limit=2 keeps only the two newest");
    }

    @Test
    void capTrimsTheOldestEntries() {
        RoleContext.set(new Caller("u-manager", "manager", Role.MANAGER));
        for (int i = 0; i < AuditService.CAPACITY + 25; i++) {
            audit.record(AuditLog.Action.PO_PAID, "PO", "PO-" + i, "entry " + i);
        }
        assertEquals(AuditService.CAPACITY, repo.findAll().size(),
                "the trail is capped like the alert feed");
        var surviving = audit.query(null, null, null, AuditLog.Action.PO_PAID, 500);
        assertEquals("entry " + (AuditService.CAPACITY + 24), surviving.get(0).getDetail(),
                "the newest entry survived");
        assertFalse(surviving.stream().anyMatch(e -> "entry 0".equals(e.getDetail())),
                "the oldest 25 entries were trimmed");
    }
}
