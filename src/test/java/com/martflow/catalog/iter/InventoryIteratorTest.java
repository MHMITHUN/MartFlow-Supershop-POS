package com.martflow.catalog.iter;

import com.martflow.catalog.Batch;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductInput;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProductFactory;
import com.martflow.common.TimeSource;
import com.martflow.persistence.InMemoryProductRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The four Iterator views over the inventory. */
class InventoryIteratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    @BeforeAll
    static void init() {
        TimeSource.useFixedClock(Clock.fixed(
                Instant.parse("2026-08-15T06:00:00Z"), TimeSource.ZONE));
        InventoryCatalog.resetForTesting();
        InventoryCatalog catalog = InventoryCatalog.initialize(new InMemoryProductRepository());
        UnitProductFactory factory = new UnitProductFactory();
        // in stock, cheap
        catalog.addProduct(factory.create("t1", input("T1", "8940001", "Cheap Soap", "20", "25", "40", 5)));
        // out of stock
        catalog.addProduct(factory.create("t2", input("T2", "8940002", "Sold Out Biscuit", "20", "30", "0", 5)));
        // low stock (stock == reorder level)
        catalog.addProduct(factory.create("t3", input("T3", "8940003", "Rare Water", "10", "15", "8", 8)));
        // expensive + batch expiring in 5 days (within default 14-day window)
        Product t4 = catalog.addProduct(factory.create("t4",
                input("T4", "8940004", "Pricey Ghee", "465", "520", "10", 2)));
        t4.addBatch(new Batch("B-T4", TODAY.plusDays(5), new BigDecimal("10")));
        // batch expiring in 40 days — outside the 14-day window
        Product t5 = catalog.addProduct(factory.create("t5",
                input("T5", "8940005", "Safe Juice", "80", "95", "20", 2)));
        t5.addBatch(new Batch("B-T5", TODAY.plusDays(40), new BigDecimal("20")));
    }

    @AfterAll
    static void tearDown() {
        TimeSource.resetToSystemClock();
        InventoryCatalog.resetForTesting();
    }

    private static ProductInput input(String sku, String barcode, String name,
                                      String cost, String mrp, String stock, int reorder) {
        return new ProductInput(sku, barcode, name, null, "staples", null, ProductUnit.PIECE,
                new BigDecimal(cost), new BigDecimal(mrp), new BigDecimal(stock), reorder);
    }

    private List<String> ids(ProductIterator iterator) {
        List<String> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next().getId());
        }
        return result;
    }

    @Test
    void inStockViewSkipsSoldOutItems() {
        List<String> visible = ids(InventoryCatalog.getInstance().browseInStock());
        assertFalse(visible.contains("t2"));
        assertTrue(visible.contains("t1"));
        assertTrue(visible.contains("t3"));
    }

    @Test
    void lowStockViewReturnsItemsAtOrBelowReorderLevel() {
        // t1: 40 > 5 no; t2: 0 <= 5 yes; t3: 8 <= 8 yes; t4: 10 > 2 no; t5: 20 > 2 no
        assertEquals(List.of("t2", "t3"), ids(InventoryCatalog.getInstance().browseLowStock()));
    }

    @Test
    void expiringViewCatchesBatchesWithinTheWindow() {
        assertEquals(List.of("t4"), ids(InventoryCatalog.getInstance().browseExpiring(14)));
        // widening the window pulls t5 in too
        List<String> wider = ids(InventoryCatalog.getInstance().browseExpiring(60));
        assertTrue(wider.contains("t4") && wider.contains("t5"));
    }

    @Test
    void priceRangeViewFiltersBySellingPrice() {
        List<String> cheap = ids(InventoryCatalog.getInstance().browseUnder(new BigDecimal("30")));
        assertTrue(cheap.contains("t1") && cheap.contains("t3"));
        assertFalse(cheap.contains("t4"));
    }

    @Test
    void resetAllowsTheSameViewToBeWalkedAgain() {
        ProductIterator iterator = InventoryCatalog.getInstance().browseInStock();
        List<String> first = ids(iterator);
        iterator.reset();
        assertEquals(first, ids(iterator));
    }
}
