package com.martflow.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Single choke point for money arithmetic. All amounts in MartFlow are BDT {@link BigDecimal}
 * with scale 2 (HALF_UP). Quantities of weighed goods use scale 3.
 *
 * <p>Rules enforced here (and nowhere else):
 * <ul>
 *   <li>never compare BigDecimals with {@code equals} — scale differences break it; use {@link #eq};</li>
 *   <li>always round through {@link #round} so totals stay reproducible;</li>
 *   <li>parse from String, never from double, to avoid binary artefacts.</li>
 * </ul>
 */
public final class MoneyUtil {

    public static final int MONEY_SCALE = 2;
    public static final int QUANTITY_SCALE = 3;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private MoneyUtil() {
    }

    /** Parses a decimal string, rejecting {@code null}/blank as zero. */
    public static BigDecimal of(String value) {
        if (value == null || value.isBlank()) {
            return ZERO;
        }
        return new BigDecimal(value.trim());
    }

    /** Rounds a money amount to 2dp HALF_UP. */
    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Rounds a quantity of weighed goods to 3dp HALF_UP. */
    public static BigDecimal roundQty(BigDecimal quantity) {
        return quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    /** Scale-insensitive equality (1.5 == 1.50). */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }

    /** {@code a > b}, scale-insensitive. */
    public static boolean gt(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0;
    }

    /** {@code a >= b}, scale-insensitive. */
    public static boolean gte(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0;
    }

    /** {@code a < b}, scale-insensitive. */
    public static boolean lt(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }

    /** Null-safe zero check. */
    public static boolean isZero(BigDecimal a) {
        return a == null || a.compareTo(BigDecimal.ZERO) == 0;
    }

    /** Null-safe positivity check. */
    public static boolean isPositive(BigDecimal a) {
        return a != null && a.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Null-safe negativity check. */
    public static boolean isNegative(BigDecimal a) {
        return a != null && a.compareTo(BigDecimal.ZERO) < 0;
    }

    /** Safeguard for required, positive amounts coming from the API layer. */
    public static BigDecimal requirePositive(BigDecimal value, String field) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(field + " must be a positive amount, got: " + value);
        }
        return Objects.requireNonNull(value);
    }
}
