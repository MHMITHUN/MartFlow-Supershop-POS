package com.martflow.billing;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.martflow.common.TimeSource;
import com.martflow.loyalty.Customer;
import com.martflow.persistence.InMemoryPromotionRepository;
import com.martflow.pricing.Promotion;
import com.martflow.pricing.PromotionEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bill math: unit lines, weighed lines (1.25 kg), promotions, coupons, fees — hand-checked. */
class BillPricingTest {

    private final InMemoryPromotionRepository promotions = new InMemoryPromotionRepository();
    private final PromotionEngine engine = new PromotionEngine(promotions);
    private final Bill bill = new Bill();

    private static final LocalDate TODAY = TimeSource.today();

    private final UnitProduct coke = new UnitProduct("p-coke", "SKU-COKE", null, "Coca-Cola 1.25L",
            null, "beverages", null, ProductUnit.PACK, new BigDecimal("78"), new BigDecimal("90"),
            BigDecimal.TEN, 5);
    private final UnitProduct salt = new UnitProduct("p-salt", "SKU-SALT", null, "Salt 1kg",
            null, "staples", null, ProductUnit.PACK, new BigDecimal("24"), new BigDecimal("30"),
            BigDecimal.TEN, 5);
    private final WeighedProduct potato = new WeighedProduct("w-pot", "SKU-POT", null, "Potato per kg",
            null, "fresh", null, ProductUnit.KG, new BigDecimal("28"), new BigDecimal("35"),
            new BigDecimal("80"), 20);

    @AfterAll
    static void resetPromotionsClock() {
        // promotions seeded relative to the real clock; nothing global was changed
    }

    @BeforeEach
    void cleanPromotions() {
        promotions.findAll().forEach(p -> promotions.delete(p.getId()));
    }

    private void seedCategorySale(String categoryId, String percent) {
        promotions.save(new Promotion("prm-t1", "Sale", Promotion.Type.CATEGORY_SALE,
                categoryId, new BigDecimal(percent), null, null,
                TODAY.minusDays(1), TODAY.plusDays(1), true));
    }

    @Test
    void unitLineIsQuantityTimesMrp() {
        bill.addItem(new UnitLine(coke, 2));
        Bill.Totals totals = bill.totals(engine);
        assertEquals(new BigDecimal("180.00"), totals.gross());
        assertEquals(new BigDecimal("180.00"), totals.net());
        assertEquals(0, totals.vat().compareTo(new BigDecimal("23.48"))); // 180 x 15/115
    }

    @Test
    void weighedLineIsWeightTimesPerKg() {
        bill.addItem(new WeighedLine(potato, new BigDecimal("1.25")));
        Bill.Totals totals = bill.totals(engine);
        assertEquals(new BigDecimal("43.75"), totals.net()); // 1.25 kg x 35
        assertEquals(0, totals.vat().compareTo(BigDecimal.ZERO)); // fresh = 0%
    }

    @Test
    void categorySaleDiscountsOnlyThatCategory() {
        seedCategorySale("beverages", "10");
        bill.addItem(new UnitLine(coke, 1));   // 90 -> 81
        bill.addItem(new UnitLine(salt, 1));   // 30 -> 30 (staples untouched)
        Bill.Totals totals = bill.totals(engine);
        assertEquals(new BigDecimal("120.00"), totals.gross());
        assertEquals(new BigDecimal("9.00"), totals.lineDiscount());
        assertEquals(new BigDecimal("111.00"), totals.net());
    }

    @Test
    void memberPriceAppliesWhenCustomerAttached() {
        promotions.save(new Promotion("prm-t2", "Member", Promotion.Type.MEMBER_PRICE,
                null, new BigDecimal("5"), null, null, TODAY.minusDays(1), null, true));
        bill.addItem(new UnitLine(coke, 1));
        bill.setCustomer(new Customer("cust-x", "Rina", "017", null, 100, TODAY, true));

        Bill.Totals totals = bill.totals(engine);
        assertEquals(new BigDecimal("85.50"), totals.net()); // 90 -5%
        assertEquals(new BigDecimal("4.50"), totals.lineDiscount());
    }

    @Test
    void couponSubtractsFromNetButNeverBelowZero() {
        bill.addItem(new UnitLine(salt, 1)); // 30
        promotions.save(new Promotion("prm-t3", "BIG", Promotion.Type.COUPON_FLAT,
                null, null, new BigDecimal("50"), "BIG",
                TODAY.minusDays(1), TODAY.plusDays(1), true));
        bill.setCouponCode("BIG");
        Bill.Totals totals = bill.totals(engine);
        assertEquals(new BigDecimal("30.00"), totals.coupon()); // capped at net
        assertEquals(new BigDecimal("0.00"), totals.net());
    }

    @Test
    void carryBagsAndDeliveryAreAddedAsFees() {
        bill.addItem(new UnitLine(salt, 1)); // 30
        bill.setCarryBags(2);                // 2 x 5
        bill.setDeliveryFee(new BigDecimal("30"));
        Bill.Totals totals = bill.totals(engine);
        assertEquals(new BigDecimal("40.00"), totals.fees());
        assertEquals(new BigDecimal("70.00"), totals.net());
    }

    @Test
    void vatMathMatchesNbrBackCalculation() {
        assertEquals(new BigDecimal("15.00"), VatCalculator.vatOf(new BigDecimal("115"), new BigDecimal("15")));
        assertEquals(new BigDecimal("7.50"), VatCalculator.vatOf(new BigDecimal("107.50"), new BigDecimal("7.5")));
        assertEquals(new BigDecimal("0.00"), VatCalculator.vatOf(new BigDecimal("99"), BigDecimal.ZERO));
    }

    @Test
    void pricedLinesExposeDecoratedDescribeForTheTillScreen() {
        seedCategorySale("beverages", "10");
        bill.addItem(new UnitLine(coke, 1));
        BillableItem priced = bill.totals(engine).pricedLines().get(0);
        assertEquals(true, priced.describe().contains("-10%"));
        assertEquals(new BigDecimal("81.00"), priced.lineNet());
    }
}
