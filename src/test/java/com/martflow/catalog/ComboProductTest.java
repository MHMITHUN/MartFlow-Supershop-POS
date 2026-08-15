package com.martflow.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The Composite pattern over combos/hampers: pricing, scarcest stock and fan-out operations. */
class ComboProductTest {

    private UnitProduct oil(double cost, double mrp, int stock) {
        return new UnitProduct("p-oil", "SKU-OIL", "8940000000001", "Oil 5L", null, "staples",
                null, ProductUnit.PACK, BigDecimal.valueOf(cost), BigDecimal.valueOf(mrp),
                BigDecimal.valueOf(stock), 5);
    }

    @Test
    void fixedPriceWinsOverComponentSum() {
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(oil(810, 850, 10), oil(100, 120, 10)), new BigDecimal("900"));
        assertEquals(0, combo.getPrice().compareTo(new BigDecimal("900")));
    }

    @Test
    void priceIsComponentSumWhenNoFixedPrice() {
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(oil(810, 850, 10), oil(100, 120, 10)), null);
        assertEquals(0, combo.getPrice().compareTo(new BigDecimal("970")));
    }

    @Test
    void stockIsTheScarcestComponent() {
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(oil(810, 850, 10), oil(100, 120, 3)), null);
        assertEquals(0, combo.getStock().compareTo(BigDecimal.valueOf(3)));
    }

    @Test
    void consumeFansOutToEveryComponent() {
        UnitProduct a = oil(810, 850, 10);
        UnitProduct b = oil(100, 120, 8);
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(a, b), null);
        combo.consume(BigDecimal.valueOf(2));
        assertEquals(0, a.getStock().compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, b.getStock().compareTo(BigDecimal.valueOf(6)));
    }

    @Test
    void consumeFailsWhenAnyComponentIsShort() {
        UnitProduct a = oil(810, 850, 10);
        UnitProduct b = oil(100, 120, 1);
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(a, b), null);
        assertThrows(IllegalStateException.class, () -> combo.consume(BigDecimal.valueOf(2)));
        // the component that could be consumed must NOT have been touched (b is checked after a)
        assertEquals(0, a.getStock().compareTo(BigDecimal.valueOf(10)));
    }

    @Test
    void restoreFansOutToEveryComponent() {
        UnitProduct a = oil(810, 850, 10);
        UnitProduct b = oil(100, 120, 8);
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(a, b), null);
        combo.restore(BigDecimal.ONE);
        assertEquals(0, a.getStock().compareTo(BigDecimal.valueOf(11)));
        assertEquals(0, b.getStock().compareTo(BigDecimal.valueOf(9)));
    }

    @Test
    void comboInComboIsRejected() {
        UnitProduct leaf = oil(810, 850, 10);
        ComboProduct inner = new ComboProduct("c-in", "SKU-IN", null, "Inner", null, "staples",
                List.of(leaf), null);
        assertThrows(IllegalArgumentException.class, () -> new ComboProduct("c-out", "SKU-OUT",
                null, "Outer", null, "staples", List.of(inner), null));
    }

    @Test
    void restockOnComboIsRejected() {
        ComboProduct combo = new ComboProduct("c1", "SKU-C1", null, "Hamper", null, "staples",
                List.of(oil(810, 850, 10)), null);
        assertThrows(UnsupportedOperationException.class,
                () -> combo.restock(BigDecimal.ONE, null));
    }
}
