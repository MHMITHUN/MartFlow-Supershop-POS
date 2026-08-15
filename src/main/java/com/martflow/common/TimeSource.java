package com.martflow.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The one source of "now" for the whole application, pinned to Asia/Dhaka (MartFlow is a
 * Bangladeshi supershop product — the business day is the Dhaka day).
 *
 * <p>Every report boundary, receipt number and expiry check goes through here. Tests can pin a
 * fixed clock via {@link #useFixedClock(Clock)} so date logic is deterministic.
 */
public final class TimeSource {

    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private static volatile Clock clock = Clock.system(ZONE);

    private TimeSource() {
    }

    /** Current business instant (Asia/Dhaka). */
    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Current business date (Asia/Dhaka). */
    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Start of the current business day. */
    public static LocalDateTime startOfToday() {
        return today().atStartOfDay();
    }

    /** End of the current business day (exclusive boundary of tomorrow). */
    public static LocalDateTime endOfToday() {
        return today().plusDays(1).atStartOfDay();
    }

    /** Pins a fixed clock — test only. */
    public static void useFixedClock(Clock fixed) {
        clock = fixed;
    }

    /** Restores the real Dhaka system clock — test only. */
    public static void resetToSystemClock() {
        clock = Clock.system(ZONE);
    }
}
