package com.martflow.reports;

import com.martflow.catalog.Batch;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.iter.ProductIterator;
import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;

import java.util.ArrayList;
import java.util.List;

/**
 * The two inventory walks, served by the catalog's custom Iterators (so the report and the
 * live "low stock"/"expiring" views can never disagree):
 * <ul>
 *   <li>{@link LowStock} — items at/below reorder level (the reorder worksheet);</li>
 *   <li>{@link Expiry} — batches expiring within a window (the wastage watch).</li>
 * </ul>
 */
public final class InventoryReports {

    private InventoryReports() {
    }

    /** Reorder worksheet: item, on-hand stock, reorder level, gap. */
    public static final class LowStock extends AbstractReportGenerator {

        private final InventoryCatalog catalog;

        public LowStock(Repository<Sale> sales, InventoryCatalog catalog) {
            super(sales);
            this.catalog = catalog;
        }

        @Override
        public String name() {
            return "Low Stock (Reorder Worksheet)";
        }

        @Override
        protected List<String> headers() {
            return List.of("Item", "SKU", "On Hand", "Reorder Level", "Order To");
        }

        @Override
        protected List<List<String>> aggregate(List<Sale> sales) {
            List<List<String>> rows = new ArrayList<>();
            ProductIterator iterator = catalog.browseLowStock();
            while (iterator.hasNext()) {
                Product product = iterator.next();
                java.math.BigDecimal onHand = product.getStock();
                java.math.BigDecimal target = java.math.BigDecimal.valueOf(product.getReorderLevel() * 2);
                rows.add(List.of(
                        product.getName(),
                        product.getSku() == null ? "" : product.getSku(),
                        onHand.stripTrailingZeros().toPlainString(),
                        String.valueOf(product.getReorderLevel()),
                        target.subtract(onHand).max(java.math.BigDecimal.ZERO)
                                .stripTrailingZeros().toPlainString()));
            }
            return rows;
        }
    }

    /** Wastage watch: batches expiring within {@code days} (default 14). */
    public static final class Expiry extends AbstractReportGenerator {

        private final InventoryCatalog catalog;
        private final int days;

        public Expiry(Repository<Sale> sales, InventoryCatalog catalog, int days) {
            super(sales);
            this.catalog = catalog;
            this.days = days;
        }

        @Override
        public String name() {
            return "Expiring Batches (" + days + " days)";
        }

        @Override
        protected List<String> headers() {
            return List.of("Item", "Batch", "Expiry", "Qty", "Status");
        }

        @Override
        protected List<List<String>> aggregate(List<Sale> sales) {
            List<List<String>> rows = new ArrayList<>();
            ProductIterator iterator = catalog.browseExpiring(days);
            while (iterator.hasNext()) {
                Product product = iterator.next();
                for (Batch batch : product.getBatches()) {
                    if (batch.expiry() == null
                            || batch.expiry().isAfter(com.martflow.common.TimeSource.today().plusDays(days))) {
                        continue;
                    }
                    boolean expired = batch.expiry().isBefore(com.martflow.common.TimeSource.today());
                    rows.add(List.of(
                            product.getName(),
                            batch.batchNo(),
                            batch.expiry().toString(),
                            batch.receivedQty().stripTrailingZeros().toPlainString(),
                            expired ? "EXPIRED — pull off shelf" : "Markdown / return"));
                }
            }
            return rows;
        }
    }
}
