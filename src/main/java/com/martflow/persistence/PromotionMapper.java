package com.martflow.persistence;

import com.martflow.pricing.Promotion;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Maps {@link Promotion} documents (dates as ISO strings). */
public class PromotionMapper implements DocumentMapper<Promotion> {

    @Override
    public Document toDocument(Promotion p) {
        return new Document("_id", p.getId())
                .append("name", p.getName())
                .append("type", p.getType().name())
                .append("categoryId", p.getCategoryId())
                .append("percentOff", p.getPercentOff() == null ? null : p.getPercentOff().toPlainString())
                .append("flatAmount", p.getFlatAmount() == null ? null : p.getFlatAmount().toPlainString())
                .append("code", p.getCode())
                .append("startsOn", p.getStartsOn() == null ? null : p.getStartsOn().toString())
                .append("endsOn", p.getEndsOn() == null ? null : p.getEndsOn().toString())
                .append("active", p.isActive());
    }

    @Override
    public Promotion fromDocument(Document d) {
        return new Promotion(
                d.getString("_id"),
                d.getString("name"),
                Promotion.Type.valueOf(d.getString("type")),
                d.getString("categoryId"),
                dec(d.getString("percentOff")),
                dec(d.getString("flatAmount")),
                d.getString("code"),
                date(d.getString("startsOn")),
                date(d.getString("endsOn")),
                d.getBoolean("active", true));
    }

    @Override
    public String idOf(Promotion promotion) {
        return promotion.getId();
    }

    private static BigDecimal dec(String raw) {
        return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
    }

    private static LocalDate date(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
        } catch (Exception unparseable) {
            return null;
        }
    }
}
