package com.martflow.suppliers;

import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.inventory.InventoryService;
import com.martflow.persistence.InMemoryProductRepository;
import com.martflow.persistence.InMemoryPurchaseOrderRepository;
import com.martflow.persistence.InMemorySupplierRepository;
import com.martflow.persistence.InMemoryTemplateRepository;
import com.martflow.security.Caller;
import com.martflow.security.Role;
import com.martflow.security.RoleContext;
import com.martflow.suppliers.postate.PurchaseOrderState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The purchasing cycle end to end: draft, submit, partial GRN (stock + batch + cost), finish
 * receiving, pay, close — plus the standing-order Prototype and payables math.
 */
class PurchasingFlowTest {

    private static PurchasingService purchasing;
    private static InventoryService inventory;
    private static com.martflow.inventory.AlertService alertService;
    private static String mainSupplier;
    private static UnitProduct water;
    private static UnitProduct chanachur;

    @BeforeAll
    static void init() {
        RoleContext.set(new Caller("u-manager", "manager", Role.MANAGER));
        InventoryCatalog.resetForTesting();
        var rawRepo = new InMemoryProductRepository();
        water = new UnitProduct("p-water", "SKU-W", null, "Water 500ml", null, "beverages",
                "SUP-003", ProductUnit.PACK, new BigDecimal("12"), new BigDecimal("15"),
                new BigDecimal("8"), 24);
        chanachur = new UnitProduct("p-chan", "SKU-C", null, "Chanachur 200g", null, "snacks",
                "SUP-003", ProductUnit.PACK, new BigDecimal("42"), new BigDecimal("50"),
                new BigDecimal("34"), 20);
        rawRepo.save(water);
        rawRepo.save(chanachur);
        InventoryCatalog catalog = InventoryCatalog.initialize(rawRepo);
        inventory = new InventoryService(catalog);

        alertService = new com.martflow.inventory.AlertService(200);
        water.subscribe(alertService);
        chanachur.subscribe(alertService);

        purchasing = new PurchasingService(
                new InMemoryPurchaseOrderRepository(), new InMemorySupplierRepository(),
                new InMemoryTemplateRepository(), inventory,
                new com.martflow.inventory.ExpiryWatcher(catalog));
        mainSupplier = purchasing.registerSupplier("Pran Distributor", "02-9664411",
                "Ruma Akter", "Net 30", "Tejgaon").getId();
    }

    @AfterAll
    static void tearDown() {
        RoleContext.clear();
        InventoryCatalog.resetForTesting();
    }

    @Test
    void fullCycleDraftSubmitReceivePayClose() {
        BigDecimal waterStockBefore = water.getStock();
        int batchesBefore = water.getBatches().size();
        PurchaseOrder po = purchasing.createDraft(mainSupplier,
                List.of(new PurchasingService.LineRequest("p-water", new BigDecimal("48"), new BigDecimal("10.50")),
                        new PurchasingService.LineRequest("p-chan", new BigDecimal("60"), new BigDecimal("36.80"))));
        assertEquals("DRAFT", po.getStatus());
        assertEquals(0, po.orderedTotal().compareTo(new BigDecimal("2712.00"))); // 48x10.50 + 60x36.80
        assertEquals(0, po.payables().compareTo(new BigDecimal("2712.00")));

        // receiving before submit is illegal
        IllegalStateException early = assertThrows(IllegalStateException.class,
                () -> purchasing.receive(po.getPoNo(), List.of()));
        assertTrue(early.getMessage().contains("cannot receive"));

        purchasing.submit(po.getPoNo());
        assertEquals("ORDERED", purchasing.order(po.getPoNo()).getStatus());

        // partial delivery: 24 water in batch B-0826 expiring next year
        purchasing.receive(po.getPoNo(), List.of(new PurchasingService.GrnLine(
                "p-water", new BigDecimal("24"), "B-0826", LocalDate.now().plusDays(360),
                new BigDecimal("11.00"))));
        PurchaseOrder partial = purchasing.order(po.getPoNo());
        assertEquals("PARTIALLY_RECEIVED", partial.getStatus());
        assertEquals(0, water.getStock().compareTo(waterStockBefore.add(new BigDecimal("24"))));
        assertEquals(0, water.getCostPrice().compareTo(new BigDecimal("11.00"))); // last cost
        assertEquals(batchesBefore + 1, water.getBatches().size());

        // over-receiving the outstanding amount is rejected
        assertThrows(IllegalArgumentException.class, () -> purchasing.receive(po.getPoNo(),
                List.of(new PurchasingService.GrnLine("p-water", new BigDecimal("30"), null, null, null))));

        // finish the delivery
        purchasing.receive(po.getPoNo(), List.of(
                new PurchasingService.GrnLine("p-water", new BigDecimal("24"), "B-0827", null, null),
                new PurchasingService.GrnLine("p-chan", new BigDecimal("60"), "B-0901",
                        LocalDate.now().plusDays(120), null)));
        assertEquals("RECEIVED", purchasing.order(po.getPoNo()).getStatus());

        // pay in two instalments; payables drop to zero
        purchasing.pay(po.getPoNo(), new BigDecimal("1000"), "BKASH", "advance");
        PurchaseOrder halfPaid = purchasing.order(po.getPoNo());
        assertEquals(0, halfPaid.payables().compareTo(new BigDecimal("1712.00")));
        purchasing.pay(po.getPoNo(), new BigDecimal("1712"), "CASH", "balance");
        assertEquals(0, purchasing.order(po.getPoNo()).payables().compareTo(BigDecimal.ZERO));

        purchasing.close(po.getPoNo());
        assertEquals("CLOSED", purchasing.order(po.getPoNo()).getStatus());
        assertThrows(IllegalStateException.class, () -> purchasing.cancel(po.getPoNo(), "late"));
    }

    @Test
    void standingOrderTemplateClonesIntoIndependentDrafts() {
        var templates = new InMemoryTemplateRepository();
        PurchasingService withTemplates = new PurchasingService(
                new InMemoryPurchaseOrderRepository(), new InMemorySupplierRepository(),
                templates, inventory,
                new com.martflow.inventory.ExpiryWatcher(InventoryCatalog.getInstance()));
        String weeklySupplier = withTemplates.registerSupplier("Weekly Supplier", "017",
                "x", "Net 7", null).getId();
        templates.save(new StandingOrderTemplate("tpl-1", "Weekly Water", weeklySupplier,
                List.of(new StandingOrderTemplate.TemplateLine("p-water", null, new BigDecimal("24")))));

        PurchaseOrder first = withTemplates.fromTemplate("tpl-1");
        PurchaseOrder second = withTemplates.fromTemplate("tpl-1");
        assertEquals("DRAFT", first.getStatus());
        assertNotEquals(first.getPoNo(), second.getPoNo());
        assertEquals(first.getLines().get(0).getOrderedQty(), second.getLines().get(0).getOrderedQty());

        // receiving against one clone must not affect the other (clone independence)
        withTemplates.submit(first.getPoNo());
        withTemplates.receive(first.getPoNo(), List.of(new PurchasingService.GrnLine(
                "p-water", new BigDecimal("24"), "B-x", null, null)));
        assertEquals(BigDecimal.ZERO, second.getLines().get(0).getReceivedQty());
    }

    @Test
    void stateGuardsRejectWrongOrderFlow() {
        PurchaseOrder po = purchasing.createDraft(mainSupplier,
                List.of(new PurchasingService.LineRequest("p-chan", BigDecimal.TEN, null)));
        assertThrows(IllegalStateException.class, () -> purchasing.close(po.getPoNo()));
        assertThrows(IllegalArgumentException.class, () -> purchasing.cancel(po.getPoNo(), " "));
        purchasing.cancel(po.getPoNo(), "supplier out of stock");
        assertEquals("CANCELLED", purchasing.order(po.getPoNo()).getStatus());
        assertThrows(IllegalStateException.class, () -> purchasing.submit(po.getPoNo()));
    }

    @Test
    void payablesAcrossOpenOrders() {
        assertTrue(purchasing.totalPayables().signum() >= 0); // smoke: never negative
    }

    @Test
    void grnLandingNearExpiryBatchRaisesAlertWithinTheCycle() {
        alertService.clear();
        PurchaseOrder po = purchasing.createDraft(mainSupplier,
                List.of(new PurchasingService.LineRequest("p-chan", new BigDecimal("10"), null)));
        purchasing.submit(po.getPoNo());
        purchasing.receive(po.getPoNo(), List.of(new PurchasingService.GrnLine(
                "p-chan", new BigDecimal("10"), "B-SOON", LocalDate.now().plusDays(5), null)));
        assertTrue(alertService.all().stream().anyMatch(a ->
                        a.getEvent().getType() == com.martflow.inventory.StockEvent.Type.EXPIRY_SOON
                                && a.getEvent().getMessage().contains("B-SOON")),
                "a batch expiring in 5 days must raise EXPIRY_SOON when the GRN lands");
    }

    @Test
    void grnWithFarExpiryRaisesNoExpiryAlert() {
        alertService.clear();
        PurchaseOrder po = purchasing.createDraft(mainSupplier,
                List.of(new PurchasingService.LineRequest("p-water", new BigDecimal("6"), null)));
        purchasing.submit(po.getPoNo());
        purchasing.receive(po.getPoNo(), List.of(new PurchasingService.GrnLine(
                "p-water", new BigDecimal("6"), "B-FAR", LocalDate.now().plusDays(400), null)));
        assertTrue(alertService.all().stream().noneMatch(a ->
                        a.getEvent().getType() == com.martflow.inventory.StockEvent.Type.EXPIRY_SOON),
                "a 400-day batch must stay silent");
    }
}
