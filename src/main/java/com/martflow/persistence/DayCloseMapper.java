package com.martflow.persistence;

import com.martflow.reports.DayClose;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Maps {@link DayClose} documents: money as exact decimal strings, maps as embedded docs. */
public class DayCloseMapper implements DocumentMapper<DayClose> {

    @Override
    public Document toDocument(DayClose z) {
        return new Document("_id", z.id())
                .append("closedAt", z.closedAt() == null ? null : z.closedAt().toString())
                .append("closedBy", z.closedBy())
                .append("from", z.from().toString())
                .append("to", z.to().toString())
                .append("bills", z.bills())
                .append("gross", z.gross().toPlainString())
                .append("discount", z.discount().toPlainString())
                .append("coupon", z.coupon().toPlainString())
                .append("fees", z.fees().toPlainString())
                .append("net", z.net().toPlainString())
                .append("vat", z.vat().toPlainString())
                .append("units", z.units().toPlainString())
                .append("tenders", moneyMapToDoc(z.tenders()))
                .append("cashIn", z.cashIn().toPlainString())
                .append("changeOut", z.changeOut().toPlainString())
                .append("returnsCount", z.returnsCount())
                .append("refundTotal", z.refundTotal().toPlainString())
                .append("refundsByChannel", moneyMapToDoc(z.refundsByChannel()))
                .append("cashRefunds", z.cashRefunds().toPlainString())
                .append("voidsCount", z.voidsCount())
                .append("voidNet", z.voidNet().toPlainString())
                .append("voidCashOut", z.voidCashOut().toPlainString())
                .append("expectedDrawerCash", z.expectedDrawerCash().toPlainString())
                .append("countedCash", z.countedCash() == null ? null : z.countedCash().toPlainString())
                .append("variance", z.variance() == null ? null : z.variance().toPlainString())
                .append("note", z.note());
    }

    @Override
    public DayClose fromDocument(Document d) {
        return new DayClose(
                d.getString("_id"),
                time(d.getString("closedAt")),
                d.getString("closedBy"),
                LocalDate.parse(d.getString("from")),
                LocalDate.parse(d.getString("to")),
                d.getInteger("bills", 0),
                money(d.getString("gross")),
                money(d.getString("discount")),
                money(d.getString("coupon")),
                money(d.getString("fees")),
                money(d.getString("net")),
                money(d.getString("vat")),
                money(d.getString("units")),
                moneyMapFromDoc(d.get("tenders", Document.class)),
                money(d.getString("cashIn")),
                money(d.getString("changeOut")),
                d.getInteger("returnsCount", 0),
                money(d.getString("refundTotal")),
                moneyMapFromDoc(d.get("refundsByChannel", Document.class)),
                money(d.getString("cashRefunds")),
                d.getInteger("voidsCount", 0),
                money(d.getString("voidNet")),
                money(d.getString("voidCashOut")),
                money(d.getString("expectedDrawerCash")),
                money(d.getString("countedCash")),
                money(d.getString("variance")),
                d.getString("note") == null ? "" : d.getString("note"));
    }

    @Override
    public String idOf(DayClose entity) {
        return entity.id();
    }

    private static Document moneyMapToDoc(Map<String, BigDecimal> map) {
        Document doc = new Document();
        if (map != null) {
            map.forEach((key, value) -> doc.append(key, value.toPlainString()));
        }
        return doc;
    }

    private static Map<String, BigDecimal> moneyMapFromDoc(Document doc) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (doc != null) {
            doc.forEach((key, value) -> map.put(key, new BigDecimal(String.valueOf(value))));
        }
        return new TreeMap<>(map);
    }

    private static BigDecimal money(String raw) {
        return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
    }

    private static LocalDateTime time(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw);
        } catch (Exception unparseable) {
            return null;
        }
    }
}
