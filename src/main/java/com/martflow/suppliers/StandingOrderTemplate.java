package com.martflow.suppliers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <b>Pattern: Prototype.</b> The weekly restock list as a reusable template: a named set of
 * (product, quantity) lines for one supplier. {@link #instantiate()} clones it into a fresh
 * DRAFT purchase order — the manager adjusts quantities and submits, instead of rebuilding the
 * same order from scratch every week. Mutating the template afterwards never touches orders
 * already created from it (clone independence is the whole point).
 */
public class StandingOrderTemplate {

    private final String id;
    private String name;
    private final String supplierId;
    private final List<TemplateLine> lines;

    /** One template line: product reference + standing quantity (unit cost read at order time). */
    public record TemplateLine(String productId, String name, BigDecimal quantity) {
    }

    public StandingOrderTemplate(String id, String name, String supplierId, List<TemplateLine> lines) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Template id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Template '" + name + "' needs at least one line");
        }
        this.id = id;
        this.name = name;
        this.supplierId = supplierId;
        this.lines = new ArrayList<>(lines);
    }

    /** Clones this template into a fresh DRAFT purchase order (the Prototype payoff). */
    public PurchaseOrder instantiate(PurchaseOrderBuilder builder) {
        for (TemplateLine line : lines) {
            builder.line(line.productId(), line.quantity(), null);
        }
        return builder.build();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public List<TemplateLine> getLines() {
        return List.copyOf(lines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StandingOrderTemplate t)) return false;
        return Objects.equals(id, t.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
