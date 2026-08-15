package com.martflow.billing.validation;

import com.martflow.billing.Bill;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.billing.validation.ValidationDtos.BillingCheck;
import com.martflow.billing.validation.ValidationDtos.TenderRequest;
import com.martflow.billing.validation.ValidationDtos.ValidationResult;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.martflow.common.TimeSource;
import com.martflow.persistence.InMemoryProductRepository;
import com.martflow.persistence.InMemoryPromotionRepository;
import com.martflow.pricing.Promotion;
import com.martflow.pricing.PromotionEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Chain of Responsibility: ordered rules, first failure wins, rest never run. */
class BillingChainTest {

    private static final LocalDate TODAY = TimeSource.today();
    private static InventoryCatalog catalog;
    private static PromotionEngine engine;
    private static UnitProduct coke;
    private static WeighedProduct potato;

    @BeforeAll
    static void init() {
        InventoryCatalog.resetForTesting();
        catalog = InventoryCatalog.initialize(new InMemoryProductRepository());
        coke = new UnitProduct("p-coke", "SKU", null, "Coke", null, "beverages", null,
                ProductUnit.PACK, new BigDecimal("78"), new BigDecimal("90"), BigDecimal.TEN, 5);
        potato = new WeighedProduct("w-pot", "SKU2", null, "Potato", null, "fresh", null,
                ProductUnit.KG, new BigDecimal("28"), new BigDecimal("35"), new BigDecimal("80"), 20);
        catalog.addProduct(coke);
        catalog.addProduct(potato);
        var promotions = new InMemoryPromotionRepository();
        promotions.save(new Promotion("prm-c", "SAVE50", Promotion.Type.COUPON_FLAT,
                null, null, new BigDecimal("50"), "SAVE50",
                TODAY.minusDays(1), TODAY.plusDays(1), true));
        engine = new PromotionEngine(promotions);
    }

    @AfterAll
    static void tearDown() {
        InventoryCatalog.resetForTesting();
    }

    private BillingCheck check(Bill bill, List<TenderRequest> tenders) {
        return new BillingCheck(bill, bill.totals(engine), tenders, catalog, engine);
    }

    @Test
    void emptyBillFailsFirst() {
        Bill bill = new Bill();
        ValidationResult result = new ValidationChain(List.of(
                new Handlers.EmptyBillHandler(),
                new Handlers.StockAvailabilityHandler())).validate(check(bill, cash("100")));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("empty"));
    }

    @Test
    void insufficientStockFailsWithTheItemName() {
        Bill bill = new Bill();
        bill.addItem(new UnitLine(coke, 11)); // stock 10
        ValidationResult result = chain().validate(check(bill, cash("1000")));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("Coke"));
    }

    @Test
    void sameProductLinesAreSummedForStock() {
        Bill bill = new Bill();
        bill.addItem(new UnitLine(coke, 6));
        bill.addItem(new UnitLine(coke, 5)); // 11 > 10 together
        ValidationResult result = chain().validate(check(bill, cash("1000")));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("Insufficient stock"));
    }

    @Test
    void tinyWeighmentsAreRejected() {
        Bill bill = new Bill();
        bill.addItem(new WeighedLine(potato, new BigDecimal("0.005"))); // 5g of potato
        ValidationResult result = chain().validate(check(bill, cash("10")));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("weight"));
    }

    @Test
    void unknownCouponFailsPromotionRule() {
        Bill bill = new Bill();
        bill.addItem(new UnitLine(coke, 1));
        bill.setCouponCode("NOPE");
        ValidationResult result = chain().validate(check(bill, cash("100")));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("NOPE"));
    }

    @Test
    void pointsTenderNeedsACustomer() {
        Bill bill = new Bill();
        bill.addItem(new UnitLine(coke, 1));
        ValidationResult result = chain().validate(check(bill,
                List.of(new TenderRequest("POINTS", new BigDecimal("90"), null))));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("loyalty customer"));
    }

    @Test
    void shortTenderFailsLast() {
        Bill bill = new Bill();
        bill.addItem(new UnitLine(coke, 1)); // 90
        ValidationResult result = chain().validate(check(bill, cash("50")));
        assertFalse(result.passed());
        assertTrue(result.failure().contains("short"));
    }

    @Test
    void aValidBillPassesEverything() {
        Bill bill = new Bill();
        bill.addItem(new UnitLine(coke, 1));
        bill.addItem(new WeighedLine(potato, new BigDecimal("1.25")));
        ValidationResult result = chain().validate(check(bill, cash("200")));
        assertTrue(result.passed());
        assertEquals(null, result.failure());
    }

    private ValidationChain chain() {
        return new ValidationChain(List.of(
                new Handlers.EmptyBillHandler(),
                new Handlers.WeighmentRequiredHandler(),
                new Handlers.StockAvailabilityHandler(),
                new Handlers.PromotionEligibilityHandler(),
                new Handlers.LoyaltyCardValidHandler(),
                new Handlers.TenderSufficientHandler()));
    }

    private List<TenderRequest> cash(String amount) {
        return List.of(new TenderRequest("CASH", new BigDecimal(amount), null));
    }
}
