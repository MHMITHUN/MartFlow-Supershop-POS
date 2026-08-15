package com.martflow.security;

/**
 * Staff roles of a supershop, ordered from least to most privileged so {@link #atLeast} is a plain
 * ordinal comparison. ADMIN = shop owner, MANAGER = floor manager, CASHIER = till operator.
 *
 * <p>DEVELOPER sits <b>beside</b> CASHIER on the business ladder: it can operate the till-side
 * screens (so the pattern demos run live) but never passes a MANAGER or ADMIN gate. It
 * additionally unlocks the Developer Mode endpoints via exact match ({@link RoleGate#requireRole})
 * — it is not "above" anyone on the business ladder.
 */
public enum Role {

    CASHIER,
    DEVELOPER,
    MANAGER,
    ADMIN;

    /** {@code true} when this role holds at least {@code required} privilege. */
    public boolean atLeast(Role required) {
        return ordinal() >= required.ordinal();
    }
}
