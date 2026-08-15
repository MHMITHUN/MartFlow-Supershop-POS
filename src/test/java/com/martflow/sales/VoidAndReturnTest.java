package com.martflow.sales;

import com.martflow.billing.BillingFacade;
import com.martflow.billing.BillingSessionRegistry;
import com.martflow.billing.validation.ValidationDtos.TenderRequest;
import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.loyalty.Customer;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.persistence.InMemoryCustomerRepository;
import com.martflow.persistence.InMemoryProductRepository;
import com.martflow.persistence.InMemoryPromotionRepository;
import com.martflow.persistence.InMemoryReturnRepository;
import com.martflow.persistence.InMemorySaleRepository;
import com.martflow.pricing.PromotionEngine;
import com.martflow.returns.ReturnService;
import com.martflow.security.Caller;
import com.martflow.security.Role;
import com.martflow.security.RoleContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sales lifecycle after the till: void (full reversal through the command pipeline) and
 * partial returns (pro-rata refunds, per-line quantity tracking, status transitions).
 */
class VoidAndReturnTest {

    private static BillingFacade billing;
    private static SalesAdminService admin;
    private static ReturnService returns;
    private static LoyaltyService loyalty;
    private static InMemorySaleRepository saleRepo;
    private static UnitProduct coke;
    private static UnitProduct soap;
    private static String customerId;

    @BeforeAll
    static void init() {
        RoleContext.set(new Caller("u-manager", "manager", Role.MANAGER));
        InventoryCatalog.resetForTesting();
        var rawRepo = new InMemoryProductRepository();
        coke = new UnitProduct("p-coke", "SKU-COKE", null, "Coke", null, "beverages", null,
                ProductUnit.PACK, new BigDecimal("78"), new BigDecimal("90"), BigDecimal.TEN, 5);
        soap = new UnitProduct("p-soap", "SKU-SOAP", null, "Soap", null, "toiletries", null,
                ProductUnit.PIECE, new BigDecimal("58"), new BigDecimal("70"), BigDecimal.TEN, 5);
        rawRepo.save(coke);
        rawRepo.save(soap);
        InventoryCatalog catalog = InventoryCatalog.initialize(rawRepo);
        var inventory = new com.martflow.inventory.InventoryService(catalog);

        saleRepo = new InMemorySaleRepository();
        loyalty = new LoyaltyService(new InMemoryCustomerRepository());
        Customer customer = loyalty.register("Rina", "017", null);
        loyalty.adjust(customer.getId(), 500);
        customerId = customer.getId();

        billing = new BillingFacade(new BillingSessionRegistry(() -> 0L), catalog, inventory,
                new PromotionEngine(new InMemoryPromotionRepository()), loyalty, saleRepo,
                new ReceiptNoGenerator(saleRepo), new BigDecimal("5.00"));

        Map<com.martflow.payment.TenderType, com.martflow.payment.PaymentChannel> channels =
                new EnumMap<>(com.martflow.payment.TenderType.class);
        channels.put(com.martflow.payment.TenderType.CASH, new com.martflow.payment.CashAdapter());
        admin = new SalesAdminService(saleRepo, inventory, loyalty, channels);
        returns = new ReturnService(saleRepo, new InMemoryReturnRepository(), inventory);
    }

    @AfterAll
    static void tearDown() {
        RoleContext.clear();
        InventoryCatalog.resetForTesting();
    }

    @AfterEach
    void freshTill() {
        billing.clearBill("t");
    }

    private Sale sell(String productId, int qty) {
        billing.addLine("t", productId, qty, null);
        billing.setCustomer("t", customerId);
        return billing.tender("t", "cashier",
                List.of(new TenderRequest("CASH", new BigDecimal("10000"), null)));
    }

    @Test
    void voidReversesStockPointsAndStatus() {
        BigDecimal cokeBefore = coke.getStock();
        int pointsBefore = loyalty.findById(customerId).orElseThrow().getPointsBalance();
        Sale sale = sell("p-coke", 2);

        Sale voided = admin.voidSale(sale.getReceiptNo(), "wrong price sticker");
        assertEquals(SaleStatus.VOIDED, voided.getStatus());
        assertEquals("wrong price sticker", voided.getVoidReason());
        assertEquals(0, cokeBefore.compareTo(coke.getStock())); // back on the shelf
        assertEquals(pointsBefore, loyalty.findById(customerId).orElseThrow().getPointsBalance());
    }

    @Test
    void voidingTwiceIsRejected() {
        Sale sale = sell("p-soap", 1);
        admin.voidSale(sale.getReceiptNo(), "dup");
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> admin.voidSale(sale.getReceiptNo(), "again"));
        assertTrue(rejected.getMessage().contains("already voided"));
    }

    @Test
    void voidWithoutReasonIsRejected() {
        Sale sale = sell("p-soap", 1);
        assertThrows(IllegalArgumentException.class, () -> admin.voidSale(sale.getReceiptNo(), " "));
    }

    @Test
    void partialReturnRestocksRefundsAndTracksQuantities() {
        BigDecimal cokeBefore = coke.getStock();
        Sale sale = sell("p-coke", 4); // 4 x 90 = 360

        var first = returns.returnItems(sale.getReceiptNo(),
                List.of(new ReturnService.RequestedLine(1, BigDecimal.ONE, "dented can")),
                "CASH", "cashier");
        assertEquals(new BigDecimal("90.00"), first.getRefundAmount()); // pro-rata: 1/4 of 360
        assertEquals(SaleStatus.PARTIALLY_RETURNED,
                saleRepo.findById(sale.getReceiptNo()).orElseThrow().getStatus());
        // sold 4, returned 1: stock is 3 below where it started
        assertEquals(0, cokeBefore.subtract(BigDecimal.valueOf(3)).compareTo(coke.getStock()));

        // returning more than remains fails
        assertThrows(IllegalArgumentException.class, () -> returns.returnItems(
                sale.getReceiptNo(),
                List.of(new ReturnService.RequestedLine(1, BigDecimal.valueOf(4), "greedy")),
                "CASH", "cashier"));

        // returning the remaining 3 completes the sale's lifecycle
        var second = returns.returnItems(sale.getReceiptNo(),
                List.of(new ReturnService.RequestedLine(1, BigDecimal.valueOf(3), "rest")),
                "CASH", "cashier");
        assertEquals(new BigDecimal("270.00"), second.getRefundAmount());
        assertEquals(SaleStatus.RETURNED,
                saleRepo.findById(sale.getReceiptNo()).orElseThrow().getStatus());
    }

    @Test
    void returningAgainstVoidedReceiptIsRejected() {
        Sale sale = sell("p-soap", 1);
        admin.voidSale(sale.getReceiptNo(), "oops");
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> returns.returnItems(sale.getReceiptNo(),
                        List.of(new ReturnService.RequestedLine(1, BigDecimal.ONE, "x")),
                        "CASH", "cashier"));
        assertTrue(rejected.getMessage().contains("voided"));
    }

    @Test
    void pointsRefundChannelIsRejectedNotSilentlyConvertedToCash() {
        Sale sale = sell("p-soap", 1);
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> returns.returnItems(sale.getReceiptNo(),
                        List.of(new ReturnService.RequestedLine(1, BigDecimal.ONE, "x")),
                        "POINTS", "cashier"));
        assertTrue(rejected.getMessage().contains("POINTS refunds"),
                "points must not silently become a cash refund");
    }
}
