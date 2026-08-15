package com.martflow.persistence;

import com.martflow.payment.TenderType;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;
import com.martflow.sales.SaleStatus;
import com.martflow.sales.Tender;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Maps {@link Sale} documents: money as exact decimal strings, embedded line snapshots. */
public class SaleMapper implements DocumentMapper<Sale> {

    @Override
    public Document toDocument(Sale sale) {
        Document doc = new Document("_id", sale.getReceiptNo())
                .append("at", sale.getAt().toString())
                .append("cashier", sale.getCashierUsername())
                .append("customerId", sale.getCustomerId())
                .append("status", sale.getStatus().name())
                .append("voidReason", sale.getVoidReason())
                .append("voidedAt", sale.getVoidedAt() == null ? null : sale.getVoidedAt().toString())
                .append("returnIds", sale.getReturnIds())
                .append("totals", totalsToDoc(sale.getTotals()))
                .append("tenders", tendersToDocs(sale.getTenders()))
                .append("lines", linesToDocs(sale.getLines()));
        return doc;
    }

    @Override
    public Sale fromDocument(Document d) {
        List<SaleLine> lines = new ArrayList<>();
        for (Document ld : d.getList("lines", Document.class)) {
            lines.add(lineFromDoc(ld));
        }
        List<Tender> tenders = new ArrayList<>();
        for (Document td : d.getList("tenders", Document.class)) {
            tenders.add(new Tender(
                    TenderType.valueOf(td.getString("type")),
                    money(td.getString("amount")),
                    td.getString("transactionId"),
                    td.getString("reference")));
        }
        Document totalsDoc = d.get("totals", Document.class);
        Sale.Totals totals = new Sale.Totals(
                money(totalsDoc.getString("gross")),
                money(totalsDoc.getString("discount")),
                money(totalsDoc.getString("coupon")),
                money(totalsDoc.getString("fees")),
                money(totalsDoc.getString("roundOff")),
                money(totalsDoc.getString("net")),
                money(totalsDoc.getString("vat")),
                money(totalsDoc.getString("tendered")),
                money(totalsDoc.getString("change")));
        Sale sale = new Sale(
                d.getString("_id"),
                parseTime(d.getString("at")),
                d.getString("cashier"),
                d.getString("customerId"),
                lines,
                totals,
                tenders);
        sale.setStatus(SaleStatus.parse(d.getString("status")));
        sale.setVoidReason(d.getString("voidReason"));
        String voidedAt = d.getString("voidedAt");
        sale.setVoidedAt(voidedAt == null || voidedAt.isBlank() ? null : parseTime(voidedAt));
        for (String returnId : d.getList("returnIds", String.class)) {
            sale.getReturnIds().add(returnId);
        }
        return sale;
    }

    @Override
    public String idOf(Sale sale) {
        return sale.getReceiptNo();
    }

    // ---- helpers ----

    private static Document totalsToDoc(Sale.Totals t) {
        return new Document("gross", t.gross().toPlainString())
                .append("discount", t.discount().toPlainString())
                .append("coupon", t.coupon().toPlainString())
                .append("fees", t.fees().toPlainString())
                .append("roundOff", t.roundOff().toPlainString())
                .append("net", t.net().toPlainString())
                .append("vat", t.vat().toPlainString())
                .append("tendered", t.tendered().toPlainString())
                .append("change", t.change().toPlainString());
    }

    private static List<Document> tendersToDocs(List<Tender> tenders) {
        List<Document> docs = new ArrayList<>();
        for (Tender t : tenders) {
            docs.add(new Document("type", t.type().name())
                    .append("amount", t.amount().toPlainString())
                    .append("transactionId", t.transactionId())
                    .append("reference", t.reference()));
        }
        return docs;
    }

    private static List<Document> linesToDocs(List<SaleLine> lines) {
        List<Document> docs = new ArrayList<>();
        for (SaleLine l : lines) {
            List<Document> decorations = new ArrayList<>();
            for (SaleLine.Decoration dec : l.decorations()) {
                decorations.add(new Document("type", dec.type())
                        .append("param", dec.param() == null ? null : dec.param().toPlainString())
                        .append("amount", dec.amount() == null ? null : dec.amount().toPlainString()));
            }
            docs.add(new Document("lineNo", l.lineNo())
                    .append("kind", l.kind())
                    .append("productId", l.productId())
                    .append("sku", l.sku())
                    .append("name", l.name())
                    .append("categoryId", l.categoryId())
                    .append("vatRatePercent", l.vatRatePercent().toPlainString())
                    .append("quantity", l.quantity().toPlainString())
                    .append("unitPrice", l.unitPrice().toPlainString())
                    .append("gross", l.gross().toPlainString())
                    .append("discount", l.discount().toPlainString())
                    .append("net", l.net().toPlainString())
                    .append("vatAmount", l.vatAmount().toPlainString())
                    .append("unitCost", l.unitCost() == null ? null : l.unitCost().toPlainString())
                    .append("decorations", decorations));
        }
        return docs;
    }

    private static SaleLine lineFromDoc(Document d) {
        List<SaleLine.Decoration> decorations = new ArrayList<>();
        for (Document dec : d.getList("decorations", Document.class)) {
            decorations.add(new SaleLine.Decoration(
                    dec.getString("type"),
                    money(dec.getString("param")),
                    money(dec.getString("amount"))));
        }
        return new SaleLine(
                d.getInteger("lineNo", 0),
                d.getString("kind"),
                d.getString("productId"),
                d.getString("sku"),
                d.getString("name"),
                d.getString("categoryId"),
                money(d.getString("vatRatePercent")),
                qty(d.getString("quantity")),
                money(d.getString("unitPrice")),
                money(d.getString("gross")),
                money(d.getString("discount")),
                money(d.getString("net")),
                money(d.getString("vatAmount")),
                d.getString("unitCost") == null ? null : money(d.getString("unitCost")),
                decorations);
    }

    private static LocalDateTime parseTime(String raw) {
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception unparseable) {
            return com.martflow.common.TimeSource.now();
        }
    }

    private static BigDecimal money(String raw) {
        return raw == null || raw.isBlank() ? BigDecimal.ZERO.setScale(2) : new BigDecimal(raw);
    }

    private static BigDecimal qty(String raw) {
        return raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
    }
}
