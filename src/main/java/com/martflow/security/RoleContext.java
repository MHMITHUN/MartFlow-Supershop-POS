package com.martflow.security;

/**
 * Per-request holder of the authenticated {@link Caller}.
 *
 * <p><b>ThreadLocal, not a global field.</b> The auth filter sets the caller on the request thread
 * and MUST clear it in a {@code finally} block — Tomcat reuses worker threads, so a leaked value
 * would leak one user's authority into the next unrelated request. This replaces the previous
 * process-wide role reference, which raced under concurrent requests.
 */
public final class RoleContext {

    private static final ThreadLocal<Caller> CURRENT = new ThreadLocal<>();

    private RoleContext() {
    }

    /** Sets the caller for this request thread. Called by the auth filter only. */
    public static void set(Caller caller) {
        CURRENT.set(caller);
    }

    /** Clears the caller. Must run in the filter's {@code finally}. */
    public static void clear() {
        CURRENT.remove();
    }

    /** The authenticated caller, or {@code null} when the thread is unauthenticated. */
    public static Caller current() {
        return CURRENT.get();
    }

    /** Convenience: current caller's role, or {@code null} when unauthenticated. */
    public static Role currentRole() {
        Caller caller = CURRENT.get();
        return caller == null ? null : caller.role();
    }
}
