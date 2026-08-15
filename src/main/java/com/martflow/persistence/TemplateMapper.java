package com.martflow.persistence;

import com.martflow.suppliers.StandingOrderTemplate;
import org.bson.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Maps {@link StandingOrderTemplate} documents. */
public class TemplateMapper implements DocumentMapper<StandingOrderTemplate> {

    @Override
    public Document toDocument(StandingOrderTemplate t) {
        List<Document> lines = new ArrayList<>();
        for (StandingOrderTemplate.TemplateLine line : t.getLines()) {
            lines.add(new Document("productId", line.productId())
                    .append("name", line.name())
                    .append("quantity", line.quantity().toPlainString()));
        }
        return new Document("_id", t.getId())
                .append("name", t.getName())
                .append("supplierId", t.getSupplierId())
                .append("lines", lines);
    }

    @Override
    public StandingOrderTemplate fromDocument(Document d) {
        List<StandingOrderTemplate.TemplateLine> lines = new ArrayList<>();
        for (Document ld : d.getList("lines", Document.class)) {
            lines.add(new StandingOrderTemplate.TemplateLine(
                    ld.getString("productId"),
                    ld.getString("name"),
                    new BigDecimal(ld.getString("quantity"))));
        }
        return new StandingOrderTemplate(
                d.getString("_id"),
                d.getString("name"),
                d.getString("supplierId"),
                lines);
    }

    @Override
    public String idOf(StandingOrderTemplate template) {
        return template.getId();
    }
}
