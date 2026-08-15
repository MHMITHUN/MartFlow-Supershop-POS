package com.martflow.suppliers;

import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.common.MoneyUtil;
import com.martflow.common.TimeSource;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Pattern: Builder.</b> Assembles a valid purchase order step by step — supplier, line after
 * line with snapshot + unit cost pulled from the catalog — without any half-configured PO
 * escaping into the system. The PO number is generated on {@code build()} (PO-yyyyMMdd-NNN).
 */
public class PurchaseOrderBuilder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String supplierId;
    private final List<PurchaseOrderLine> lines = new ArrayList<>();
    private final List<String> existingPoNos;

    public PurchaseOrderBuilder(String supplierId, List<String> existingPoNos) {
        if (supplierId == null || supplierId.isBlank()) {
            throw new IllegalArgumentException("A purchase order needs a supplier");
        }
        this.supplierId = supplierId;
        this.existingPoNos = existingPoNos == null ? List.of() : existingPoNos;
    }

    /** Adds a line for a catalog product with an explicit unit cost. */
    public PurchaseOrderBuilder line(String productId, BigDecimal quantity, BigDecimal unitCost) {
        Product product = InventoryCatalog.getInstance().findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
        MoneyUtil.requirePositive(quantity, "Order quantity");
        MoneyUtil.requirePositive(unitCost == null ? product.getCostPrice() : unitCost,
                "Unit cost for " + product.getName());
        lines.add(new PurchaseOrderLine(productId, product.getSku(), product.getName(),
                quantity, BigDecimal.ZERO,
                unitCost == null ? product.getCostPrice() : MoneyUtil.round(unitCost)));
        return this;
    }

    public PurchaseOrder build() {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A purchase order needs at least one line");
        }
        return new PurchaseOrder(nextPoNo(), supplierId, lines);
    }

    private String nextPoNo() {
        String prefix = "PO-" + TimeSource.today().format(DAY) + "-";
        int max = existingPoNos.stream()
                .filter(no -> no != null && no.startsWith(prefix))
                .mapToInt(no -> {
                    try {
                        return Integer.parseInt(no.substring(prefix.length()));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        return prefix + String.format("%03d", max + 1);
    }
}
