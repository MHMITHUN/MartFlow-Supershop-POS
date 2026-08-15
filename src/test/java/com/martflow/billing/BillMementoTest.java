package com.martflow.billing;

import com.martflow.billing.item.UnitLine;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.loyalty.Customer;
import com.martflow.persistence.InMemoryPromotionRepository;
import com.martflow.pricing.PromotionEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cashier's Undo (Memento): snapshots before every destructive edit, capped stack. */
class BillMementoTest {

    private final PromotionEngine engine = new PromotionEngine(new InMemoryPromotionRepository());
    private final BillingSession session = new BillingSession(0);

    private final UnitProduct salt = new UnitProduct("p-salt", "SKU-SALT", null, "Salt 1kg",
            null, "staples", null, ProductUnit.PACK, new BigDecimal("24"), new BigDecimal("30"),
            BigDecimal.TEN, 5);

    @Test
    void undoRestoresRemovedLine() {
        session.bill().addItem(new UnitLine(salt, 1));
        session.bill().addItem(new UnitLine(salt, 2));
        session.snapshot();
        session.bill().removeItem(0); // cashier removes the wrong line
        assertEquals(1, session.bill().lineCount());

        assertTrue(session.undo());
        assertEquals(2, session.bill().lineCount());
        assertEquals(new BigDecimal("90.00"), session.bill().totals(engine).gross()); // 1x30 + 2x30 restored
    }

    @Test
    void undoRestoresClearedBill() {
        session.bill().addItem(new UnitLine(salt, 3));
        Customer customer = new Customer("cust-1", "Rina", "017", null, 10, LocalDate.now(), true);
        session.bill().setCustomer(customer);
        session.bill().setCouponCode("X");
        session.snapshot();
        session.bill().clear();

        assertTrue(session.undo());
        assertEquals(1, session.bill().lineCount());
        assertEquals("Rina", session.bill().customer().getName());
        assertEquals("X", session.bill().couponCode());
    }

    @Test
    void undoStackIsCappedAtTen() {
        for (int i = 0; i < 15; i++) {
            session.snapshot();
            session.bill().addItem(new UnitLine(salt, 1));
        }
        assertEquals(10, session.undoDepth());
        int undos = 0;
        while (session.undo()) {
            undos++;
        }
        assertEquals(10, undos);
        // after 10 undos the bill is back to 5 lines (15 adds - 10 undos)
        assertEquals(5, session.bill().lineCount());
    }

    @Test
    void resetAfterSaleEmptiesEverything() {
        session.bill().addItem(new UnitLine(salt, 1));
        session.snapshot();
        session.resetAfterSale();
        assertEquals(0, session.bill().lineCount());
        assertEquals(0, session.undoDepth());
        assertFalse(session.undo());
    }
}
