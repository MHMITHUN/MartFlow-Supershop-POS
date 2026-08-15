package com.martflow.suppliers;

import com.martflow.common.TimeSource;
import com.martflow.suppliers.postate.PurchaseOrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A purchase order to a supplier. The status string is the source of truth for persistence;
 * the {@link PurchaseOrderState} object (State pattern) is derived per call and guards every
 * transition.
 */
public class PurchaseOrder {

    private final String poNo; // id
    private final String supplierId;
    private String status;
    private final List<PurchaseOrderLine> lines;
    private final List<Payment> payments;
    private final LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime closedAt;
    private String cancelReason;

    /** One supplier payment recorded against this PO. */
    public record Payment(BigDecimal amount, String method, String note, LocalDateTime at) {
    }

    public PurchaseOrder(String poNo, String supplierId, List<PurchaseOrderLine> lines) {
        this.poNo = poNo;
        this.supplierId = supplierId;
        this.status = PurchaseOrderState.DRAFT.name();
        this.lines = new ArrayList<>(lines);
        this.payments = new ArrayList<>();
        this.createdAt = TimeSource.now();
    }

    public PurchaseOrder(String poNo, String supplierId, String status,
                         List<PurchaseOrderLine> lines, List<Payment> payments,
                         LocalDateTime createdAt, LocalDateTime submittedAt,
                         LocalDateTime receivedAt, LocalDateTime closedAt, String cancelReason) {
        this.poNo = poNo;
        this.supplierId = supplierId;
        this.status = status;
        this.lines = new ArrayList<>(lines);
        this.payments = new ArrayList<>(payments);
        this.createdAt = createdAt;
        this.submittedAt = submittedAt;
        this.receivedAt = receivedAt;
        this.closedAt = closedAt;
        this.cancelReason = cancelReason;
    }

    /** The guarding state object for the current status (State pattern in action). */
    public PurchaseOrderState state() {
        return PurchaseOrderState.fromName(status);
    }

    public void transitionTo(PurchaseOrderState next) {
        this.status = next.name();
    }

    public String getPoNo() {
        return poNo;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public String getStatus() {
        return status;
    }

    public List<PurchaseOrderLine> getLines() {
        return List.copyOf(lines);
    }

    /** Internal access for the GRN service (received-qty accumulation). */
    PurchaseOrderLine mutableLine(String productId) {
        for (PurchaseOrderLine line : lines) {
            if (line.getProductId().equals(productId)) {
                return line;
            }
        }
        throw new IllegalArgumentException("PO " + poNo + " has no line for product " + productId);
    }

    public List<Payment> getPayments() {
        return List.copyOf(payments);
    }

    void addPayment(Payment payment) {
        payments.add(payment);
    }

    /** Total ordered value: sum of ordered qty x unit cost. */
    public BigDecimal orderedTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            total = total.add(line.lineTotal());
        }
        return total;
    }

    /** What the shop still owes: ordered total minus payments (negative = advance paid). */
    public BigDecimal payables() {
        BigDecimal paid = BigDecimal.ZERO;
        for (Payment payment : payments) {
            paid = paid.add(payment.amount());
        }
        return orderedTotal().subtract(paid);
    }

    /** {@code true} when every line has fully arrived. */
    public boolean fullyReceived() {
        for (PurchaseOrderLine line : lines) {
            if (!line.isFullyReceived()) {
                return false;
            }
        }
        return true;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
