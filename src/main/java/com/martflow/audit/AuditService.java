package com.martflow.audit;

import com.martflow.common.TimeSource;
import com.martflow.persistence.Repository;
import com.martflow.security.Caller;
import com.martflow.security.RoleContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The shop's activity trail: every consequential action (logins, voids, returns, shrinkage,
 * catalog and price edits, promotions, purchase-order moves, staff changes) recorded with its
 * actor and intent.
 *
 * <p><b>Why a plain service and not another Observer:</b> the existing stock observers fire per
 * inventory event with no actor and no intent — a manual restock and a GRN-triggered one are
 * indistinguishable there, and a five-line bill would write five rows. Audit entries are written
 * at the API boundary, immediately after the action succeeds — the one place that reliably knows
 * both the caller and the reason. (Startup seeding writes to the services directly, bypassing
 * the API, so a first boot leaves no fake trail.)
 *
 * <p>The trail is capped like the alert feed ({@link #CAPACITY}): it is a shift-level
 * operations record for the owner, not a forensic ledger.
 */
public class AuditService {

    /** Mirrors AlertService's ring semantics: a shift-level trail, not unbounded history. */
    static final int CAPACITY = 500;

    private final Repository<AuditLog> logs;

    public AuditService(Repository<AuditLog> logs) {
        this.logs = logs;
    }

    /** Records an action by the current caller; "-" when no caller is on the thread. */
    public synchronized void record(AuditLog.Action action, String targetType, String targetId,
                                    String detail) {
        Caller caller = RoleContext.current();
        append(caller == null ? "-" : caller.username(),
                caller == null ? "-" : caller.role().name(),
                action, targetType, targetId, detail);
    }

    /** Records an auth event where the actor is the typed username, not an authenticated caller. */
    public synchronized void recordAttempt(String username, AuditLog.Action action, String detail) {
        append(username == null || username.isBlank() ? "-" : username, "-", action,
                "AUTH", username, detail);
    }

    private void append(String actor, String role, AuditLog.Action action,
                        String targetType, String targetId, String detail) {
        logs.save(new AuditLog("aud-" + System.nanoTime(), TimeSource.now(), actor, role,
                action, targetType, targetId, detail));
        List<AuditLog> all = logs.findAll();
        if (all.size() > CAPACITY) {
            all.sort(Comparator.comparing(AuditLog::getAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 0; i < all.size() - CAPACITY; i++) {
                logs.delete(all.get(i).getId());
            }
        }
    }

    /** Newest-first trail with optional date window, actor and action filters. */
    public List<AuditLog> query(LocalDate from, LocalDate to, String actor,
                                AuditLog.Action action, int limit) {
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime endExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        return logs.findAll().stream()
                .filter(e -> start == null || !e.getAt().isBefore(start))
                .filter(e -> endExclusive == null || e.getAt().isBefore(endExclusive))
                .filter(e -> actor == null || actor.isBlank()
                        || actor.equalsIgnoreCase(e.getActorUsername()))
                .filter(e -> action == null || e.getAction() == action)
                .sorted(Comparator.comparing(AuditLog::getAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit <= 0 ? 200 : Math.min(limit, CAPACITY))
                .toList();
    }
}
