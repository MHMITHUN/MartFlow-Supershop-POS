package com.martflow.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * The Z-report: one closed business window's complete money picture — sales aggregates, the
 * per-tender split, returns and voids, and the drawer reconciliation
 * ({@code expectedDrawerCash} vs {@code countedCash} vs {@code variance}).
 *
 * <p>A preview instance carries {@code null} id/closedAt/closedBy/countedCash/variance; a saved
 * close fills them in.
 *
 * @param tenders      per-tender totals of the window's sales, keyed by tender type name
 * @param refundsByChannel refund totals keyed by channel name
 */
public record DayClose(
        String id,
        LocalDateTime closedAt,
        String closedBy,
        LocalDate from,
        LocalDate to,
        int bills,
        BigDecimal gross,
        BigDecimal discount,
        BigDecimal coupon,
        BigDecimal fees,
        BigDecimal net,
        BigDecimal vat,
        BigDecimal units,
        Map<String, BigDecimal> tenders,
        BigDecimal cashIn,
        BigDecimal changeOut,
        int returnsCount,
        BigDecimal refundTotal,
        Map<String, BigDecimal> refundsByChannel,
        BigDecimal cashRefunds,
        int voidsCount,
        BigDecimal voidNet,
        BigDecimal voidCashOut,
        BigDecimal expectedDrawerCash,
        BigDecimal countedCash,
        BigDecimal variance,
        String note) {
}
