package com.martflow.inventory;

import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductInput;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProductFactory;
import com.martflow.common.NotFoundException;
import com.martflow.persistence.InMemoryProductRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Observer wiring: products fire events, the alert center records them, the reorder
 *  suggester turns low stock into an actionable suggestion, and InventoryService persists every
 *  stock move (the old "stock lost on restart" bug). Every test starts from a stock baseline of
 *  20 so the class is order-independent. */
class AlertObserverTest {

    private static final AlertService ALERTS = new AlertService(50);
    private static final InventoryCatalog CATALOG;

    static {
        InventoryCatalog.resetForTesting();
        CATALOG = InventoryCatalog.initialize(new InMemoryProductRepository());
        Product water = new UnitProductFactory().create("p-water", new ProductInput("SKU-W",
                "8943333333331", "Miner Water 500ml", null, "beverages", null, ProductUnit.PACK,
                new BigDecimal("12"), new BigDecimal("15"), new BigDecimal("20"), 10));
        CATALOG.addProduct(water);
        water.subscribe(ALERTS);
        water.subscribe(new ReorderSuggestionObserver(ALERTS));
    }

    private final InventoryService inventory = new InventoryService(CATALOG);

    @AfterAll
    static void tearDown() {
        InventoryCatalog.resetForTesting();
    }

    @BeforeEach
    void baseline() {
        // Reset the shared product to stock 20 (count correction) with a clean feed, so every
        // test starts from the same state regardless of execution order. Note adjust() removes
        // for positive deltas and adds for negative ones, hence the negation.
        ALERTS.clear();
        Product water = CATALOG.findById("p-water").orElseThrow();
        BigDecimal change = BigDecimal.valueOf(20).subtract(water.getStock());
        if (change.signum() != 0) {
            inventory.adjust("p-water", StockAdjustment.Reason.COUNT, change.negate(), "test baseline");
        }
        ALERTS.clear();
    }

    @Test
    void restockFiresRestockEventAndPersistsThroughCatalog() {
        inventory.restock("p-water", BigDecimal.TEN, "B-01", null);
        assertEquals(0, CATALOG.findById("p-water").orElseThrow().getStock()
                .compareTo(BigDecimal.valueOf(30)));
        assertTrue(ALERTS.all().stream()
                .anyMatch(a -> a.getEvent().getType() == StockEvent.Type.RESTOCK));
    }

    @Test
    void crossingReorderLevelFiresLowStockPlusReorderSuggestion() {
        // 20 on hand, reorder level 10: consume 11 -> 9 (crosses)
        inventory.consume("p-water", BigDecimal.valueOf(11));
        long lowStockEvents = ALERTS.all().stream()
                .filter(a -> a.getEvent().getType() == StockEvent.Type.LOW_STOCK)
                .count();
        assertEquals(2, lowStockEvents); // the raw event + the reorder suggestion
        assertTrue(ALERTS.all().stream()
                .anyMatch(a -> a.getEvent().getMessage().contains("Reorder suggestion")));
    }

    @Test
    void consumingAboveReorderLevelFiresNothing() {
        inventory.consume("p-water", BigDecimal.valueOf(5)); // 20 -> 15, still above 10
        assertEquals(0, ALERTS.all().size());
    }

    @Test
    void priceChangeFiresEvent() {
        Product water = CATALOG.findById("p-water").orElseThrow();
        BigDecimal nextPrice = water.getPrice().add(BigDecimal.ONE);
        inventory.applyPrice("p-water", nextPrice);
        assertTrue(ALERTS.all().stream()
                .anyMatch(a -> a.getEvent().getType() == StockEvent.Type.PRICE_CHANGE));
        assertEquals(0, water.getPrice().compareTo(nextPrice));
    }

    @Test
    void shrinkageAdjustmentRemovesStockAndRaisesEvent() {
        inventory.adjust("p-water", StockAdjustment.Reason.DAMAGE, BigDecimal.valueOf(2),
                "broken bottles");
        assertEquals(0, CATALOG.findById("p-water").orElseThrow().getStock()
                .compareTo(BigDecimal.valueOf(18)));
        assertTrue(ALERTS.all().stream()
                .anyMatch(a -> a.getEvent().getType() == StockEvent.Type.SHRINKAGE));
    }

    @Test
    void feedIsCappedAndSupportsReadFlags() {
        AlertService small = new AlertService(3);
        for (int i = 0; i < 6; i++) {
            small.update(StockEvent.custom(StockEvent.Type.RESTOCK, "p", "P", null, null, "e" + i));
        }
        List<AlertService.Alert> feed = small.all();
        assertEquals(3, feed.size()); // oldest evicted

        AlertService.Alert newest = feed.get(feed.size() - 1);
        assertFalse(newest.isRead());
        assertTrue(small.markRead(newest.getId()));
        assertTrue(newest.isRead());
        assertEquals(2, small.unread().size());
        assertFalse(small.markRead("nope"));
    }

    @Test
    void consumingMoreThanStockThrowsWithoutMutation() {
        Product water = CATALOG.findById("p-water").orElseThrow();
        BigDecimal before = water.getStock();
        assertThrows(IllegalStateException.class,
                () -> inventory.consume("p-water", before.add(BigDecimal.TEN)));
        assertEquals(0, before.compareTo(water.getStock()));
    }

    @Test
    void unknownProductIsNotFound() {
        assertThrows(NotFoundException.class, () -> inventory.consume("nope", BigDecimal.ONE));
    }
}
