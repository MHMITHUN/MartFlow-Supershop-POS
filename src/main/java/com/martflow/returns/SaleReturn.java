package com.martflow.returns;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** A processed return against one receipt: lines, refund amount, refund channel. */
public class SaleReturn {

    private final String id;
    private final String receiptNo;
    private final LocalDateTime at;
    private final String cashierUsername;
    private final List<ReturnLine> lines;
    private final BigDecimal refundAmount;
    private final String refundChannel;   // tender type name
    private final String refundTransactionId;

    public SaleReturn(String id, String receiptNo, LocalDateTime at, String cashierUsername,
                      List<ReturnLine> lines, BigDecimal refundAmount, String refundChannel,
                      String refundTransactionId) {
        this.id = id;
        this.receiptNo = receiptNo;
        this.at = at == null ? com.martflow.common.TimeSource.now() : at;
        this.cashierUsername = cashierUsername;
        this.lines = List.copyOf(lines);
        this.refundAmount = refundAmount;
        this.refundChannel = refundChannel;
        this.refundTransactionId = refundTransactionId;
    }

    public String getId() {
        return id;
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

    public List<ReturnLine> getLines() {
        return lines;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getRefundChannel() {
        return refundChannel;
    }

    public String getRefundTransactionId() {
        return refundTransactionId;
    }
}
