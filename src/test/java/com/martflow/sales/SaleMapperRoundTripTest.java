package com.martflow.sales;

import com.martflow.persistence.SaleMapper;
import org.junit.jupiter.api.Test;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sale persistence fidelity: document &rarr; domain &rarr; document round trip keeps every
 * decimal, line and decoration intact — the guarantee that a reprinted receipt from Mongo shows
 * the same numbers as the day it was printed.
 */
class SaleMapperRoundTripTest {

    @Test
    void roundTripPreservesEverything() {
        SaleLine line = new SaleLine(
                1, "UNIT", "p-coke", "SKU-COKE", "Coke", "beverages",
                new BigDecimal("15"), BigDecimal.valueOf(2), new BigDecimal("90"),
                new BigDecimal("180.00"), new BigDecimal("18.00"), new BigDecimal("162.00"),
                new BigDecimal("21.13"), new BigDecimal("78.00"),
                List.of(new SaleLine.Decoration("LINE_DISCOUNT", new BigDecimal("10"),
                        new BigDecimal("18.00"))));
        Sale.Totals totals = new Sale.Totals(
                new BigDecimal("180.00"), new BigDecimal("18.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("-0.50"), new BigDecimal("161.50"),
                new BigDecimal("21.13"), new BigDecimal("200.00"), new BigDecimal("38.50"));
        Sale sale = new Sale("MF-20260815-001", LocalDateTime.of(2026, 8, 15, 14, 30),
                "cashier", "cust-1", List.of(line), totals,
                List.of(new Tender(com.martflow.payment.TenderType.CASH,
                        new BigDecimal("200.00"), "CASH-x", "MF-20260815-001")));

        SaleMapper mapper = new SaleMapper();
        Document doc = mapper.toDocument(sale);
        Sale back = mapper.fromDocument(doc);

        assertEquals(sale.getReceiptNo(), back.getReceiptNo());
        assertEquals(sale.getAt(), back.getAt());
        assertEquals(sale.getCashierUsername(), back.getCashierUsername());
        assertEquals(sale.getCustomerId(), back.getCustomerId());
        assertEquals(sale.getStatus(), back.getStatus());

        SaleLine backLine = back.getLines().get(0);
        assertEquals(line.name(), backLine.name());
        assertEquals(line.vatRatePercent(), backLine.vatRatePercent());
        assertEquals(line.net(), backLine.net());
        assertEquals(line.vatAmount(), backLine.vatAmount()); // exact decimal strings, not doubles
        assertEquals(line.decorations().get(0).type(), backLine.decorations().get(0).type());
        assertEquals(line.decorations().get(0).param(), backLine.decorations().get(0).param());

        assertEquals(totals.net(), back.getTotals().net());
        assertEquals(totals.roundOff(), back.getTotals().roundOff());
        assertEquals(totals.change(), back.getTotals().change());
        assertEquals(sale.getTenders().get(0).type(), back.getTenders().get(0).type());
        assertEquals(sale.getTenders().get(0).amount(), back.getTenders().get(0).amount());
    }

    @Test
    void lenientStatusParsingKeepsListsAlive() {
        assertEquals(SaleStatus.COMPLETED, SaleStatus.parse(null));
        assertEquals(SaleStatus.COMPLETED, SaleStatus.parse(""));
        assertEquals(SaleStatus.COMPLETED, SaleStatus.parse("HAND-EDITED-NONSENSE"));
        assertEquals(SaleStatus.VOIDED, SaleStatus.parse("voided"));
    }

    @Test
    void voidMetadataSurvivesTheRoundTrip() {
        Sale sale = new Sale("MF-20260816-009", LocalDateTime.of(2026, 8, 16, 14, 30),
                "cashier", null, List.of(),
                new Sale.Totals(new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), BigDecimal.ZERO,
                        new BigDecimal("100.00"), BigDecimal.ZERO),
                List.of(new Tender(com.martflow.payment.TenderType.CASH,
                        new BigDecimal("100.00"), null, null)));
        sale.setStatus(SaleStatus.VOIDED);
        sale.setVoidReason("chargeback");
        LocalDateTime voidedAt = LocalDateTime.of(2026, 8, 17, 9, 15);
        sale.setVoidedAt(voidedAt);

        Sale back = new SaleMapper().fromDocument(new SaleMapper().toDocument(sale));

        assertEquals(SaleStatus.VOIDED, back.getStatus());
        assertEquals("chargeback", back.getVoidReason());
        assertEquals(voidedAt, back.getVoidedAt()); // the Z-report's cross-midnight math depends on it
    }
}
