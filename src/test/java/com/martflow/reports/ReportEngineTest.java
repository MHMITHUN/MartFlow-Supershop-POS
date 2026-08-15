package com.martflow.reports;

import com.martflow.common.TimeSource;
import com.martflow.persistence.InMemorySaleRepository;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;
import com.martflow.sales.SaleStatus;
import com.martflow.sales.Tender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The report engine: the Template Method's hook order, VOIDED sales never counting as revenue,
 * units counted by quantity (not line count), and hand-checked VAT/profit math through the
 * visitors over reconstructed sale lines.
 *
 * <p>Fixture (kept consistent: net = qty x unitPrice):
 * <ul>
 *   <li>MF-1: 2 x 115.00 @15% VAT  -> net 230.00, output VAT 30.00, cost 2x90.00=180.00</li>
 *   <li>MF-2: 3 x 107.50 @7.5% VAT -> net 322.50, output VAT 22.50, cost 3x82.50=247.50</li>
 *   <li>MF-3: voided, must never appear</li>
 * </ul>
 */
class ReportEngineTest {

    private static final LocalDate TODAY = TimeSource.today();
    private static InMemorySaleRepository sales;

    @BeforeAll
    static void seed() {
        sales = new InMemorySaleRepository();
        sales.save(sale("MF-1", "cashier", 2, new BigDecimal("115.00"),
                new BigDecimal("90.00"), new BigDecimal("15")));
        sales.save(sale("MF-2", "cashier", 3, new BigDecimal("107.50"),
                new BigDecimal("82.50"), new BigDecimal("7.5")));
        Sale voided = sale("MF-3", "manager", 5, new BigDecimal("100.00"),
                new BigDecimal("50.00"), new BigDecimal("15"));
        voided.setStatus(SaleStatus.VOIDED);
        sales.save(voided);
    }

    @AfterAll
    static void reset() {
        TimeSource.resetToSystemClock();
    }

    private static Sale sale(String receiptNo, String cashier, int qty, BigDecimal unitPrice,
                             BigDecimal unitCost, BigDecimal vatRate) {
        BigDecimal net = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = net.multiply(vatRate)
                .divide(vatRate.add(new BigDecimal("100")), 2, RoundingMode.HALF_UP);
        SaleLine line = new SaleLine(1, "UNIT", "p-x", "SKU-X", "Item X", "beverages",
                vatRate, BigDecimal.valueOf(qty), unitPrice, net, BigDecimal.ZERO, net, vat,
                unitCost, List.of());
        Sale.Totals totals = new Sale.Totals(net, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, net, vat, net, BigDecimal.ZERO);
        return new Sale(receiptNo, LocalDateTime.now(), cashier, null, List.of(line), totals,
                List.of(new Tender(com.martflow.payment.TenderType.CASH, net, null, receiptNo)));
    }

    @Test
    void templateRunsFetchAggregateBuildInOrder() {
        RecordingReport report = new RecordingReport(sales);
        report.generate(TODAY, TODAY);
        assertEquals(List.of("fetch", "aggregate", "build"), report.visibleTrail());
    }

    @Test
    void voidedSalesNeverCountAsRevenue() {
        StaffPerformanceReport report = new StaffPerformanceReport(sales);
        ReportResult result = report.generate(TODAY, TODAY);
        assertTrue(result.rows().stream().anyMatch(r -> r.get(0).equals("cashier") && r.get(1).equals("2")));
        assertTrue(result.rows().stream().noneMatch(r -> r.get(0).equals("manager")));
    }

    @Test
    void unitsAreQuantitiesNotLineCounts() {
        StaffPerformanceReport report = new StaffPerformanceReport(sales);
        ReportResult result = report.generate(TODAY, TODAY);
        String cashierUnits = result.rows().stream()
                .filter(r -> r.get(0).equals("cashier")).findFirst().orElseThrow().get(2);
        assertEquals("5", cashierUnits); // 2 + 3, not 2 lines
    }

    @Test
    void vatSummaryGroupsByRateWithHandCheckedMath() {
        VatSummaryReport report = new VatSummaryReport(sales);
        ReportResult result = report.generate(TODAY, TODAY);
        assertTrue(result.rows().stream().anyMatch(r ->
                r.get(0).equals("15") && r.get(2).equals("30.00")));
        assertTrue(result.rows().stream().anyMatch(r ->
                r.get(0).equals("7.5") && r.get(2).equals("22.50")));
        assertEquals("52.50", result.meta().get("totalOutputVat"));
    }

    @Test
    void profitReportComputesMarginFromSaleTimeCosts() {
        ProfitReport report = new ProfitReport(sales);
        ReportResult result = report.generate(TODAY, TODAY);
        // MF-1: net 230, vat 30 -> revenue ex VAT 200.00; cost 2x90 = 180.00 -> profit 20.00
        List<String> row = result.rows().stream()
                .filter(r -> r.get(0).equals("MF-1")).findFirst().orElseThrow();
        assertEquals("200.00", row.get(2));
        assertEquals("180.00", row.get(3));
        assertEquals("20.00", row.get(4));
        // window totals across both sales: revenue 500.00, cost 427.50, profit 72.50
        assertEquals("500.00", result.meta().get("totalRevenue"));
        assertEquals("427.50", result.meta().get("totalCogs"));
        assertEquals("72.50", result.meta().get("totalProfit"));
    }

    @Test
    void dailySalesAggregatesPerDay() {
        DailySalesReport report = new DailySalesReport(sales);
        ReportResult result = report.generate(TODAY, TODAY);
        assertEquals(1, result.rows().size());
        List<String> day = result.rows().get(0);
        assertEquals("2.00", day.get(1)); // bills (voided excluded)
        assertEquals("552.50", day.get(6)); // net 230 + 322.50
    }

    @Test
    void bestSellersRankByUnits() {
        BestSellersReport report = new BestSellersReport(sales);
        ReportResult result = report.generate(TODAY, TODAY);
        assertEquals("Item X", result.rows().get(0).get(0));
        assertEquals("5", result.rows().get(0).get(2));
        assertEquals("552.50", result.rows().get(0).get(3));
    }

    /** Exposes the template's hook trail for the order test. */
    static final class RecordingReport extends AbstractReportGenerator {
        RecordingReport(InMemorySaleRepository sales) {
            super(sales);
        }

        List<String> visibleTrail() {
            return List.copyOf(trail);
        }

        @Override
        public String name() {
            return "Recording";
        }

        @Override
        protected List<String> headers() {
            return List.of("x");
        }

        @Override
        protected List<List<String>> aggregate(List<Sale> sales) {
            return List.of();
        }
    }
}
