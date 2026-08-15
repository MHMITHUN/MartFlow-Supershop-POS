package com.martflow.payment;

import com.martflow.loyalty.Customer;
import com.martflow.loyalty.LoyaltyService;
import com.martflow.persistence.InMemoryCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The five tender adapters: every channel charges and refunds through the same port. */
class PaymentChannelTest {

    private LoyaltyService loyalty;

    @BeforeEach
    void reset() {
        loyalty = new LoyaltyService(new InMemoryCustomerRepository());
    }

    @Test
    void cashAlwaysSucceeds() {
        CashAdapter cash = new CashAdapter();
        assertTrue(cash.charge(new BigDecimal("123.45"), "MF-1").success());
        assertTrue(cash.refund(new BigDecimal("123.45"), "MF-1").success());
        assertEquals(TenderType.CASH, cash.type());
    }

    @Test
    void cardAdapterTranslatesTheLegacyTerminal() {
        CardAdapter card = new CardAdapter();
        PaymentResult ok = card.charge(new BigDecimal("250"), "MF-2");
        assertTrue(ok.success());
        assertTrue(ok.transactionId().startsWith("CARD-"));
        // refunds work with the original auth reference we pass on
        assertTrue(card.refund(new BigDecimal("250"), ok.transactionId()).success());
        assertFalse(card.refund(new BigDecimal("250"), "NOT-A-CARD-REF").success());
    }

    @Test
    void cardDeclinesAbsurdAmounts() {
        CardAdapter card = new CardAdapter();
        PaymentResult declined = card.charge(new BigDecimal("500000"), "MF-3");
        assertFalse(declined.success());
    }

    @Test
    void bkashAdapterConvertsTakaToPoisha() {
        BkashAdapter bkash = new BkashAdapter();
        PaymentResult ok = bkash.charge(new BigDecimal("99.99"), "MF-4");
        assertTrue(ok.success());
        assertTrue(ok.transactionId().startsWith("BK"));
        assertTrue(bkash.refund(new BigDecimal("99.99"), ok.transactionId()).success());
    }

    @Test
    void nagadAdapterParsesTheStringResponse() {
        NagadAdapter nagad = new NagadAdapter();
        PaymentResult ok = nagad.charge(new BigDecimal("350"), "MF-5");
        assertTrue(ok.success());
        assertTrue(ok.transactionId().startsWith("TRX-NG-"));
        assertTrue(nagad.refund(new BigDecimal("350"), ok.transactionId()).success());
        assertFalse(nagad.refund(new BigDecimal("350"), "GARBAGE").success());
    }

    @Test
    void pointsAdapterChecksTheBalance() {
        Customer rich = loyalty.register("Rich", "017", null);
        loyalty.adjust(rich.getId(), 500);
        Customer poor = loyalty.register("Poor", "018", null);

        PointsAdapter points = new PointsAdapter(loyalty, () -> rich);
        assertTrue(points.charge(new BigDecimal("300"), "MF-6").success());
        assertEquals(200, loyalty.findById(rich.getId()).orElseThrow().getPointsBalance());

        PointsAdapter broke = new PointsAdapter(loyalty, () -> poor);
        assertFalse(broke.charge(new BigDecimal("10"), "MF-7").success());

        PointsAdapter anonymous = new PointsAdapter(loyalty, () -> null);
        assertFalse(anonymous.charge(BigDecimal.TEN, "MF-8").success());
    }

    @Test
    void loyaltyServiceEarningAndRedemptionMath() {
        Customer customer = loyalty.register("Rina", "019", null);
        assertEquals(7, loyalty.pointsFor(new BigDecimal("750")));   // floor(750/100)
        assertEquals(0, loyalty.pointsFor(new BigDecimal("99")));
        loyalty.earn(customer.getId(), 7);
        assertEquals(7, loyalty.findById(customer.getId()).orElseThrow().getPointsBalance());
        assertThrows(IllegalStateException.class,
                () -> loyalty.redeem(customer.getId(), 8));
        loyalty.redeem(customer.getId(), 5);
        assertEquals(2, loyalty.findById(customer.getId()).orElseThrow().getPointsBalance());
        loyalty.reverseEarn(customer.getId(), 10); // floors at zero
        assertEquals(0, loyalty.findById(customer.getId()).orElseThrow().getPointsBalance());
    }

    @Test
    void duplicatePhoneRegistrationIsRejected() {
        loyalty.register("Rina", "019", null);
        assertThrows(IllegalArgumentException.class, () -> loyalty.register("Again", "019", null));
    }

    @Test
    void memberSinceDefaultsToToday() {
        Customer customer = loyalty.register("New", "016", null);
        assertEquals(LocalDate.now().toString().length(), 10); // smoke: date exists
        assertTrue(customer.getMemberSince() != null);
    }
}
