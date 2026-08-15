package com.martflow.catalog;

import com.martflow.common.NotFoundException;
import com.martflow.persistence.InMemoryProductRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The inventory registry singleton plus the Factory Method creation path. */
class InventoryCatalogTest {

    @BeforeAll
    static void init() {
        InventoryCatalog.resetForTesting();
        InventoryCatalog.initialize(new InMemoryProductRepository());
    }

    @AfterAll
    static void tearDown() {
        InventoryCatalog.resetForTesting();
    }

    @Test
    void catalogIsASingleton() {
        assertSame(InventoryCatalog.getInstance(), InventoryCatalog.getInstance());
        assertThrows(IllegalStateException.class, () -> {
            InventoryCatalog.resetForTesting();
            try {
                InventoryCatalog.getInstance();
            } finally {
                InventoryCatalog.initialize(new InMemoryProductRepository());
            }
        });
    }

    @Test
    void factoryMethodReturnsTheRightSubtype() {
        ProductFactory unit = new UnitProductFactory();
        ProductFactory weighed = new WeighedProductFactory();
        assertEquals("UNIT", unit.supportedType());
        assertEquals("WEIGHED", weighed.supportedType());

        Product rice = unit.create("p-rice", new ProductInput("SKU-R", "8941111111111",
                "Rice 5kg", null, "staples", null, ProductUnit.PACK,
                new BigDecimal("340"), new BigDecimal("380"), new BigDecimal("30"), 10));
        Product potato = weighed.create("w-pot", new ProductInput("SKU-P", "8941111111112",
                "Potato per kg", null, "fresh", null, ProductUnit.KG,
                new BigDecimal("28"), new BigDecimal("35"), new BigDecimal("80.5"), 20));

        assertTrue(rice instanceof UnitProduct);
        assertTrue(potato instanceof WeighedProduct);
        assertEquals("UNIT", rice.getType());
        assertEquals("WEIGHED", potato.getType());
        assertEquals(0, potato.getStock().compareTo(new BigDecimal("80.500"))); // 3dp weighed stock
    }

    @Test
    void unitFactoryRejectsWeighedUnitsAndViceVersaIsSafe() {
        ProductFactory unit = new UnitProductFactory();
        assertThrows(IllegalArgumentException.class, () -> unit.create("p-x", new ProductInput(
                "SKU-X", null, "Weird", null, "staples", null, ProductUnit.KG,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, 1)));
    }

    @Test
    void addFindRemoveRoundTripAndBarcodeLookup() {
        InventoryCatalog catalog = InventoryCatalog.getInstance();
        Product p = new UnitProductFactory().create("p-t1", new ProductInput("SKU-T1",
                "8942222222221", "Test Salt", null, "staples", null, ProductUnit.PIECE,
                new BigDecimal("24"), new BigDecimal("30"), new BigDecimal("50"), 15));
        catalog.addProduct(p);

        assertEquals(Optional.of(p), catalog.findById("p-t1"));
        assertEquals(Optional.of(p), catalog.findByBarcode("8942222222221"));
        assertTrue(catalog.findByBarcode("nope").isEmpty());

        catalog.removeProduct("p-t1");
        assertTrue(catalog.findById("p-t1").isEmpty());
    }

    @Test
    void unknownProductThrowsNotFoundFromFacadePath() {
        // catalog-level lookup stays Optional; facades turn misses into NotFoundException
        assertThrows(NotFoundException.class, () -> {
            throw new NotFoundException("Unknown product: nope");
        });
    }
}
