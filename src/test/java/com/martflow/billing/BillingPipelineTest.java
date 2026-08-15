package com.martflow.billing;

import com.martflow.billing.validation.ValidationDtos.TenderRequest;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.martflow.common.NotFoundException;
import com.martflow.loyalty.Customer;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.payment.CardAdapter;
import com.martflow.payment.PaymentChannel;
import com.martflow.payment.PaymentResult;
import com.martflow.payment.TenderType;
import com.martflow.persistence.InMemoryCustomerRepository;
import com.martflow.persistence.InMemoryProductRepository;
import com.martflow.persistence.InMemoryPromotionRepository;
import com.martflow.persistence.InMemorySaleRepository;
import com.martflow.pricing.PromotionEngine;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tender pipeline end to end (unit level): a successful tender persists stock movements,
 * the sale and loyalty points; ANY failure (declined card) rolls all of it back.
 */
class BillingPipelineTest {

    private static BillingFacade billing;
    private static InMemoryProductRepository rawRepo;
    private static InMemorySaleRepository saleRepo;
    private static LoyaltyService loyalty;
    private static UnitProduct coke;
    private static WeighedProduct potato;
    private static String customerId;

    @BeforeAll
    static void init() {
        InventoryCatalog.resetForTesting();
        rawRepo = new InMemoryProductRepository();
        coke = new UnitProduct("p-coke", "SKU-COKE", null, "Coke", null, "beverages", null,
                ProductUnit.PACK, new BigDecimal("78"), new BigDecimal("90"), BigDecimal.TEN, 5);
        potato = new WeighedProduct("w-pot", "SKU-POT", null, "Potato", null, "fresh", null,
                ProductUnit.KG, new BigDecimal("28"), new BigDecimal("35"), new BigDecimal("80"), 20);
        rawRepo.save(coke);
        rawRepo.save(potato);
        InventoryCatalog catalog = InventoryCatalog.initialize(rawRepo);

        saleRepo = new InMemorySaleRepository();
        loyalty = new LoyaltyService(new InMemoryCustomerRepository());
        Customer customer = loyalty.register("Rina", "017", null);
        loyalty.adjust(customer.getId(), 500);
        customerId = customer.getId();

        billing = new BillingFacade(new BillingSessionRegistry(() -> 0L), catalog,
                new com.martflow.inventory.InventoryService(catalog),
                new PromotionEngine(new InMemoryPromotionRepository()),
                loyalty, saleRepo, new com.martflow.sales.ReceiptNoGenerator(saleRepo),
                new BigDecimal("5.00"));
        billing.registerChannel(new CardAdapter());
    }

    @AfterAll
    static void tearDown() {
        InventoryCatalog.resetForTesting();
    }

    /** A channel that always declines — proves the rollback path. */
    private static void installDecliningCard() {
        billing.registerChannel(new PaymentChannel() {
            @Override
            public TenderType type() {
                return TenderType.CARD;
            }

            @Override
            public PaymentResult charge(BigDecimal amount, String reference) {
                return PaymentResult.failed("Card declined (test)");
            }

            @Override
            public PaymentResult refund(BigDecimal amount, String reference) {
                return PaymentResult.ok("refunded", "test refund");
            }
        });
    }

    private static void installWorkingCard() {
        billing.registerChannel(new CardAdapter());
    }

    @Test
    void successfulTenderPersistsStockSaleAndPoints() {
        billing.clearBill("t1");
        billing.addLine("t1", "p-coke", 2, null);            // 180
        billing.addLine("t1", "w-pot", null, new BigDecimal("1.25")); // 43.75
        billing.setCustomer("t1", customerId);
        int pointsBefore = loyalty.findById(customerId).orElseThrow().getPointsBalance();

        Sale sale = billing.tender("t1", "cashier",
                List.of(new TenderRequest("CASH", new BigDecimal("1000"), null)));

        // cash -> rounded to whole taka: 223.75 -> 224 (roundOff +0.25)
        assertEquals(new BigDecimal("224"), sale.getTotals().net());
        assertEquals(new BigDecimal("0.25"), sale.getTotals().roundOff());
        assertEquals(new BigDecimal("776.00"), sale.getTotals().change());
        assertEquals(SaleStatus.COMPLETED, sale.getStatus());

        // stock movements persisted: reload the catalog fresh from the repository
        InventoryCatalog.resetForTesting();
        InventoryCatalog reloaded = InventoryCatalog.initialize(rawRepo);
        assertEquals(0, reloaded.findById("p-coke").orElseThrow().getStock()
                .compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, reloaded.findById("w-pot").orElseThrow().getStock()
                .compareTo(new BigDecimal("78.750")));
        InventoryCatalog.resetForTesting();
        InventoryCatalog.initialize(rawRepo); // restore for other tests

        // sale persisted, points earned (224/100 -> 2)
        assertTrue(saleRepo.findById(sale.getReceiptNo()).isPresent());
        assertEquals(pointsBefore + 2, loyalty.findById(customerId).orElseThrow().getPointsBalance());

        // bill cleared for the next customer
        assertEquals(0, billing.billOf("t1").lineCount());
    }

    @Test
    void declinedCardRollsBackEverything() {
        billing.clearBill("t2");
        billing.addLine("t2", "p-coke", 3, null);
        billing.setCustomer("t2", customerId);
        int pointsBefore = loyalty.findById(customerId).orElseThrow().getPointsBalance();
        BigDecimal stockBefore = coke.getStock();

        installDecliningCard();
        assertThrows(IllegalStateException.class, () -> billing.tender("t2", "cashier",
                List.of(new TenderRequest("CARD", new BigDecimal("270"), null))));
        installWorkingCard();

        assertEquals(0, stockBefore.compareTo(coke.getStock())); // stock restored
        assertEquals(pointsBefore, loyalty.findById(customerId).orElseThrow().getPointsBalance());
        assertEquals(0, saleRepo.findAll().stream()
                .filter(s -> "cashier".equals(s.getCashierUsername()) && s.getLines().stream()
                        .anyMatch(l -> "p-coke".equals(l.productId()) && l.quantity().compareTo(BigDecimal.valueOf(3)) == 0))
                .count()); // no phantom sale
    }

    @Test
    void validationFailureLeavesNoTrace() {
        billing.clearBill("t3");
        billing.addLine("t3", "p-coke", 1, null);
        BigDecimal stockBefore = coke.getStock();
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> billing.tender("t3", "cashier",
                        List.of(new TenderRequest("CASH", new BigDecimal("1"), null))));
        assertTrue(rejected.getMessage().contains("short"));
        assertEquals(0, stockBefore.compareTo(coke.getStock())); // untouched by validation failures
        assertEquals(1, billing.billOf("t3").lineCount()); // bill survives for correction
    }

    @Test
    void pointsWorkAsTender() {
        billing.clearBill("t4");
        billing.addLine("t4", "p-coke", 1, null); // 90
        billing.setCustomer("t4", customerId);
        int before = loyalty.findById(customerId).orElseThrow().getPointsBalance();
        if (before < 90) {
            loyalty.adjust(customerId, 200);
        }
        int adjusted = loyalty.findById(customerId).orElseThrow().getPointsBalance();

        Sale sale = billing.tender("t4", "cashier",
                List.of(new TenderRequest("POINTS", new BigDecimal("90"), null)));
        assertEquals(new BigDecimal("90.00"), sale.getTotals().net());
        assertEquals(TenderType.POINTS, sale.getTenders().get(0).type());
        assertEquals(adjusted - 90 + 0, loyalty.findById(customerId).orElseThrow().getPointsBalance());
    }

    @Test
    void unknownItemIsNotFound() {
        assertThrows(NotFoundException.class, () -> billing.addLine("t5", "ghost", 1, null));
    }
}
