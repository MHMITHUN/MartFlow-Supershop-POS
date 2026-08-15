package com.martflow.sales;

import com.martflow.common.TimeSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed sale: receipt number, cashier, customer, full line snapshots, totals and tenders.
 * Status/void metadata are mutable (void/return in later phases); everything else is frozen at
 * tender time.
 */
public class Sale {

    private final String receiptNo;
    private final LocalDateTime at;
    private final String cashierUsername;
    private final String customerId;
    private SaleStatus status;
    private String voidReason;
    private LocalDateTime voidedAt;
    private final List<SaleLine> lines;
    private final Totals totals;
    private final List<Tender> tenders;
    private final List<String> returnIds;

    public Sale(String receiptNo, LocalDateTime at, String cashierUsername, String customerId,
                List<SaleLine> lines, Totals totals, List<Tender> tenders) {
        this.receiptNo = receiptNo;
        this.at = at == null ? TimeSource.now() : at;
        this.cashierUsername = cashierUsername;
        this.customerId = customerId;
        this.status = SaleStatus.COMPLETED;
        this.lines = List.copyOf(lines);
        this.totals = totals;
        this.tenders = List.copyOf(tenders);
        this.returnIds = new ArrayList<>();
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public String getCashierUsername() {
        return cashierUsername;
    }

    public String getCustomerId() {
        return customerId;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public void setStatus(SaleStatus status) {
        this.status = status == null ? SaleStatus.COMPLETED : status;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    /** When the void happened — a sale sold yesterday but voided today hits today's drawer. */
    public LocalDateTime getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(LocalDateTime voidedAt) {
        this.voidedAt = voidedAt;
    }

    public List<SaleLine> getLines() {
        return lines;
    }

    public Totals getTotals() {
        return totals;
    }

    public List<Tender> getTenders() {
        return tenders;
    }

    public List<String> getReturnIds() {
        return returnIds;
    }

    /** The money summary of one sale. */
    public record Totals(
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal coupon,
            BigDecimal fees,
            BigDecimal roundOff,
            BigDecimal net,
            BigDecimal vat,
            BigDecimal tendered,
            BigDecimal change) {
    }
}
