package com.martflow.persistence;

import com.martflow.common.TimeSource;
import com.martflow.returns.ReturnLine;
import com.martflow.returns.SaleReturn;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Maps {@link SaleReturn} documents. */
public class ReturnMapper implements DocumentMapper<SaleReturn> {

    @Override
    public Document toDocument(SaleReturn r) {
        List<Document> lines = new ArrayList<>();
        for (ReturnLine line : r.getLines()) {
            lines.add(new Document("lineNo", line.saleLineNo())
                    .append("name", line.name())
                    .append("quantity", line.quantity().toPlainString())
                    .append("reason", line.reason()));
        }
        return new Document("_id", r.getId())
                .append("receiptNo", r.getReceiptNo())
                .append("at", r.getAt().toString())
                .append("cashier", r.getCashierUsername())
                .append("lines", lines)
                .append("refundAmount", r.getRefundAmount().toPlainString())
                .append("refundChannel", r.getRefundChannel())
                .append("refundTransactionId", r.getRefundTransactionId());
    }

    @Override
    public SaleReturn fromDocument(Document d) {
        List<ReturnLine> lines = new ArrayList<>();
        for (Document ld : d.getList("lines", Document.class)) {
            lines.add(new ReturnLine(
                    ld.getInteger("lineNo", 0),
                    ld.getString("name"),
                    new BigDecimal(ld.getString("quantity")),
                    ld.getString("reason")));
        }
        LocalDateTime at;
        try {
            at = LocalDateTime.parse(d.getString("at"));
        } catch (Exception unparseable) {
            at = TimeSource.now();
        }
        return new SaleReturn(
                d.getString("_id"),
                d.getString("receiptNo"),
                at,
                d.getString("cashier"),
                lines,
                new BigDecimal(d.getString("refundAmount")),
                d.getString("refundChannel"),
                d.getString("refundTransactionId"));
    }

    @Override
    public String idOf(SaleReturn saleReturn) {
        return saleReturn.getId();
    }
}
