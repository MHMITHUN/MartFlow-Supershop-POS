package com.martflow.persistence;

import com.martflow.common.TimeSource;
import com.martflow.suppliers.PurchaseOrder;
import com.martflow.suppliers.PurchaseOrderLine;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Maps {@link PurchaseOrder} documents: lines, payments and per-stage timestamps. */
public class PurchaseOrderMapper implements DocumentMapper<PurchaseOrder> {

    @Override
    public Document toDocument(PurchaseOrder po) {
        List<Document> lines = new ArrayList<>();
        for (PurchaseOrderLine line : po.getLines()) {
            lines.add(new Document("productId", line.getProductId())
                    .append("sku", line.getSku())
                    .append("name", line.getName())
                    .append("orderedQty", line.getOrderedQty().toPlainString())
                    .append("receivedQty", line.getReceivedQty().toPlainString())
                    .append("unitCost", line.getUnitCost().toPlainString()));
        }
        List<Document> payments = new ArrayList<>();
        for (PurchaseOrder.Payment payment : po.getPayments()) {
            payments.add(new Document("amount", payment.amount().toPlainString())
                    .append("method", payment.method())
                    .append("note", payment.note())
                    .append("at", payment.at().toString()));
        }
        return new Document("_id", po.getPoNo())
                .append("supplierId", po.getSupplierId())
                .append("status", po.getStatus())
                .append("lines", lines)
                .append("payments", payments)
                .append("createdAt", po.getCreatedAt().toString())
                .append("submittedAt", po.getSubmittedAt() == null ? null : po.getSubmittedAt().toString())
                .append("receivedAt", po.getReceivedAt() == null ? null : po.getReceivedAt().toString())
                .append("closedAt", po.getClosedAt() == null ? null : po.getClosedAt().toString())
                .append("cancelReason", po.getCancelReason());
    }

    @Override
    public PurchaseOrder fromDocument(Document d) {
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (Document ld : d.getList("lines", Document.class)) {
            lines.add(new PurchaseOrderLine(
                    ld.getString("productId"),
                    ld.getString("sku"),
                    ld.getString("name"),
                    dec(ld.getString("orderedQty")),
                    dec(ld.getString("receivedQty")),
                    dec(ld.getString("unitCost"))));
        }
        List<PurchaseOrder.Payment> payments = new ArrayList<>();
        for (Document pd : d.getList("payments", Document.class)) {
            payments.add(new PurchaseOrder.Payment(
                    dec(pd.getString("amount")),
                    pd.getString("method"),
                    pd.getString("note"),
                    time(pd.getString("at"))));
        }
        return new PurchaseOrder(
                d.getString("_id"),
                d.getString("supplierId"),
                d.getString("status"),
                lines,
                payments,
                time(d.getString("createdAt")),
                time(d.getString("submittedAt")),
                time(d.getString("receivedAt")),
                time(d.getString("closedAt")),
                d.getString("cancelReason"));
    }

    @Override
    public String idOf(PurchaseOrder purchaseOrder) {
        return purchaseOrder.getPoNo();
    }

    private static BigDecimal dec(String raw) {
        return raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
    }

    private static LocalDateTime time(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw);
        } catch (Exception unparseable) {
            return TimeSource.now();
        }
    }
}
