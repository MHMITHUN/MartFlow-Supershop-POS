package com.martflow.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Money arithmetic and the Dhaka time source — the two cross-cutting invariants. */
class MoneyAndTimeTest {

    @AfterEach
    void resetClock() {
        TimeSource.resetToSystemClock();
    }

    @Test
    void roundsMoneyHalfUpAtTwoDecimals() {
        assertEquals(new BigDecimal("10.13"), MoneyUtil.round(new BigDecimal("10.125")));
        assertEquals(new BigDecimal("10.12"), MoneyUtil.round(new BigDecimal("10.1249")));
        assertEquals(new BigDecimal("0.00"), MoneyUtil.round(new BigDecimal("0.001")));
    }

    @Test
    void roundsQuantitiesAtThreeDecimals() {
        assertEquals(new BigDecimal("1.257"), MoneyUtil.roundQty(new BigDecimal("1.2568")));
        assertEquals(new BigDecimal("1.250"), MoneyUtil.roundQty(new BigDecimal("1.25")));
    }

    @Test
    void equalityIsScaleInsensitive() {
        assertTrue(MoneyUtil.eq(new BigDecimal("1.5"), new BigDecimal("1.50")));
        assertFalse(MoneyUtil.eq(new BigDecimal("1.5"), new BigDecimal("1.51")));
        assertTrue(MoneyUtil.lt(new BigDecimal("1.49"), new BigDecimal("1.5")));
        assertTrue(MoneyUtil.gt(new BigDecimal("2"), new BigDecimal("1.999")));
    }

    @Test
    void parsesBlankAsZeroAndRejectsNothingElse() {
        assertEquals(0, MoneyUtil.of(null).compareTo(BigDecimal.ZERO));
        assertEquals(0, MoneyUtil.of("  ").compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("12.50"), MoneyUtil.of("12.50"));
    }

    @Test
    void requirePositiveGuardsAmounts() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.requirePositive(BigDecimal.ZERO, "amount"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.requirePositive(new BigDecimal("-5"), "amount"));
        assertEquals(new BigDecimal("5"), MoneyUtil.requirePositive(new BigDecimal("5"), "amount"));
    }

    @Test
    void timeSourcePinsToDhakaAndHonorsFixedClocks() {
        TimeSource.useFixedClock(Clock.fixed(
                Instant.parse("2026-08-15T20:30:00Z"), TimeSource.ZONE)); // 20:30 UTC = 02:30 +06 next day
        assertEquals(LocalDate.of(2026, 8, 16), TimeSource.today());
        assertEquals(LocalDateTime.of(2026, 8, 16, 2, 30, 0), TimeSource.now());
        assertEquals(LocalDateTime.of(2026, 8, 16, 0, 0, 0), TimeSource.startOfToday());
        assertEquals(LocalDateTime.of(2026, 8, 17, 0, 0, 0), TimeSource.endOfToday());
    }
}
