package com.martflow.security;

/**
 * Role check for operations guarded outside the repository proxies (e.g. financial report
 * endpoints). Reads the current caller from {@link RoleContext}; unauthenticated threads never
 * pass a gate.
 *
 * <p>Two flavours: {@link #requireAtLeast} walks the business privilege ladder;
 * {@link #requireRole} demands exact membership — used by the Developer Mode endpoints, which
 * belong to a role that is deliberately outside that ladder.
 */
public final class RoleGate {

    private RoleGate() {
    }

    /** Requires an authenticated caller with at least {@code required}, else 403. */
    public static void requireAtLeast(Role required) {
        Caller caller = RoleContext.current();
        if (caller == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!caller.isAtLeast(required)) {
            throw new AccessDeniedException("Requires role " + required + " or higher (you are "
                    + caller.username() + " / " + caller.role() + ")");
        }
    }

    /** Requires an authenticated caller with exactly {@code exact}, else 403. */
    public static void requireRole(Role exact) {
        Caller caller = RoleContext.current();
        if (caller == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (caller.role() != exact) {
            throw new AccessDeniedException("This area is restricted to the " + exact + " role (you are "
                    + caller.username() + " / " + caller.role() + ")");
        }
    }
}
