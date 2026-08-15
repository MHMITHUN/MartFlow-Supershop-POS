package com.martflow.reports;

import com.martflow.audit.AuditLog;
import com.martflow.audit.AuditService;
import com.martflow.common.TimeSource;
import com.martflow.persistence.Repository;
import com.martflow.returns.SaleReturn;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;
import com.martflow.sales.SaleStatus;
import com.martflow.sales.Tender;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * End-of-shift drawer reconciliation — the Z-report every supershop manager prints before the
 * cash goes to the safe.
 *
 * <p><b>Why not another AbstractReportGenerator subclass:</b> the report template's one honest
 * invariant is "VOIDED never counts" — but a Z-report must count voided cash (it physically left
 * the drawer), needs returns from outside its data source, and produces a typed multi-dimensional
 * aggregate (tender split + reconciliation math), not uniform string rows. Forcing it into the
 * template would break that invariant; this is a plain service instead.
 *
 * <p><b>Drawer math</b> (physical cash movements of the window):
 * <pre>expected = cashIn − changeOut − cashRefunds − voidCashOut</pre>
 * where {@code cashIn}/{@code changeOut} cover <b>all</b> sales made in the window (a sale voided
 * later the same window had its cash in the drawer, and its refund back out — net zero);
 * {@code voidCashOut} is the CASH tendered by voids whose refund landed in this window (a sale
 * sold yesterday, voided today), matching what the void pipeline actually refunds. Revenue
 * figures ({@code net}/{@code vat}/…) stay VOIDED-free — the two sets are deliberately different.
 */
public class DayCloseService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final Repository<Sale> sales;
    private final Repository<SaleReturn> returns;
    private final Repository<DayClose> closes;
    private final AuditService audit;

    public DayCloseService(Repository<Sale> sales, Repository<SaleReturn> returns,
                           Repository<DayClose> closes, AuditService audit) {
        this.sales = sales;
        this.returns = returns;
        this.closes = closes;
        this.audit = audit;
    }

    /** The window's numbers before anyone counts the drawer (manager+). */
    public DayClose preview(LocalDate from, LocalDate to) {
        RoleGate.requireAtLeast(Role.MANAGER);
        return compute(from, to, null, null, null);
    }

    /** Closes the window: re-computes server-side (client math is never trusted) and saves. */
    public DayClose close(LocalDate from, LocalDate to, BigDecimal countedCash, String note,
                          String closedBy) {
        RoleGate.requireAtLeast(Role.MANAGER);
        if (countedCash == null || countedCash.signum() < 0) {
            throw new IllegalArgumentException("Counted drawer cash must be zero or more");
        }
        DayClose closed = compute(from, to, countedCash, note, closedBy);
        closes.save(closed);
        audit.record(AuditLog.Action.DAY_CLOSED, "DAY_CLOSE", closed.id(),
                closed.bills() + " bills, variance " + closed.variance());
        return closed;
    }

    /** Newest-first history of past closes. */
    public List<DayClose> history() {
        RoleGate.requireAtLeast(Role.MANAGER);
        return closes.findAll().stream()
                .sorted(Comparator.comparing(DayClose::closedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    private DayClose compute(LocalDate from, LocalDate to, BigDecimal countedCash,
                             String note, String closedBy) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();

        List<Sale> revenueBills = new ArrayList<>();   // non-VOIDED sales of the window
        List<Sale> cashSales = new ArrayList<>();      // ALL sales made in the window (drawer view)
        List<Sale> voidedHere = new ArrayList<>();     // voids whose refund landed in the window
        for (Sale sale : sales.findAll()) {
            boolean soldHere = !sale.getAt().isBefore(start) && sale.getAt().isBefore(endExclusive);
            if (soldHere) {
                cashSales.add(sale);
            }
            if (sale.getStatus() == SaleStatus.VOIDED) {
                if (sale.getVoidedAt() != null && !sale.getVoidedAt().isBefore(start)
                        && sale.getVoidedAt().isBefore(endExclusive)) {
                    voidedHere.add(sale);
                }
            } else if (soldHere) {
                revenueBills.add(sale);
            }
        }

        Map<String, BigDecimal> tenders = new TreeMap<>();
        BigDecimal gross = ZERO, discount = ZERO, coupon = ZERO, fees = ZERO,
                net = ZERO, vat = ZERO, units = ZERO, cashIn = ZERO, changeOut = ZERO;
        for (Sale bill : revenueBills) {
            Sale.Totals t = bill.getTotals();
            gross = gross.add(t.gross());
            discount = discount.add(t.discount());
            coupon = coupon.add(t.coupon());
            fees = fees.add(t.fees());
            net = net.add(t.net());
            vat = vat.add(t.vat());
            for (SaleLine line : bill.getLines()) {
                if (!"ADJUSTMENT".equals(line.kind())) {
                    units = units.add(line.quantity());
                }
            }
        }
        for (Sale sale : cashSales) {
            for (Tender tender : sale.getTenders()) {
                tenders.merge(tender.type().name(), tender.amount(), BigDecimal::add);
                if (tender.type() == com.martflow.payment.TenderType.CASH) {
                    cashIn = cashIn.add(tender.amount());
                }
            }
            changeOut = changeOut.add(sale.getTotals().change());
        }

        int returnsCount = 0;
        BigDecimal refundTotal = ZERO, cashRefunds = ZERO;
        Map<String, BigDecimal> refundsByChannel = new TreeMap<>();
        for (SaleReturn r : returns.findAll()) {
            if (r.getAt() != null && !r.getAt().isBefore(start) && r.getAt().isBefore(endExclusive)) {
                returnsCount++;
                refundTotal = refundTotal.add(r.getRefundAmount());
                String channel = r.getRefundChannel() == null ? "CASH" : r.getRefundChannel();
                refundsByChannel.merge(channel, r.getRefundAmount(), BigDecimal::add);
                if ("CASH".equals(channel)) {
                    cashRefunds = cashRefunds.add(r.getRefundAmount());
                }
            }
        }

        BigDecimal voidNet = ZERO, voidCashOut = ZERO;
        for (Sale voided : voidedHere) {
            voidNet = voidNet.add(voided.getTotals().net());
            for (Tender tender : voided.getTenders()) {
                if (tender.type() == com.martflow.payment.TenderType.CASH) {
                    // the void pipeline refunds the full tender amount — that is what left the drawer
                    voidCashOut = voidCashOut.add(tender.amount());
                }
            }
        }

        BigDecimal expectedDrawerCash = cashIn.subtract(changeOut)
                .subtract(cashRefunds).subtract(voidCashOut);
        BigDecimal variance = countedCash == null ? null
                : countedCash.subtract(expectedDrawerCash);

        boolean isClose = countedCash != null;
        return new DayClose(
                isClose ? "zc-" + System.nanoTime() : null,
                isClose ? TimeSource.now() : null,
                closedBy,
                from, to,
                revenueBills.size(), gross, discount, coupon, fees, net, vat, units,
                tenders, cashIn, changeOut,
                returnsCount, refundTotal, refundsByChannel, cashRefunds,
                voidedHere.size(), voidNet, voidCashOut,
                expectedDrawerCash, countedCash, variance,
                note == null ? "" : note);
    }
}
