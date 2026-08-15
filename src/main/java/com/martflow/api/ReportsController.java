package com.martflow.api;

import com.martflow.catalog.InventoryCatalog;
import com.martflow.catalog.iter.ProductIterator;
import com.martflow.common.TimeSource;
import com.martflow.persistence.Repositories;
import com.martflow.reports.AbstractReportGenerator;
import com.martflow.reports.BestSellersReport;
import com.martflow.reports.DailySalesReport;
import com.martflow.reports.InventoryReports;
import com.martflow.reports.ProfitReport;
import com.martflow.reports.ReportCsvExporter;
import com.martflow.reports.ReportResult;
import com.martflow.reports.ReturnsReport;
import com.martflow.reports.StaffPerformanceReport;
import com.martflow.reports.VatSummaryReport;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleStatus;
import com.martflow.security.Role;
import com.martflow.security.RoleGate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The reporting suite. Every report renders JSON or CSV ({@code ?format=csv}); the financial
 * ones (profit, VAT, dashboard) are manager-only, the operational ones (low stock, expiry,
 * best sellers) any staff member can read.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final Map<String, AbstractReportGenerator> reports = new LinkedHashMap<>();
    private final Map<String, Role> requiredRole = new LinkedHashMap<>();

    public ReportsController(com.martflow.app.MartFlowFacade shop) {
        InventoryCatalog catalog = shop.catalog();
        var sales = Repositories.sales();
        register("daily-sales", new DailySalesReport(sales), Role.CASHIER);
        register("best-sellers", new BestSellersReport(sales), Role.CASHIER);
        register("low-stock", new InventoryReports.LowStock(sales, catalog), Role.CASHIER);
        register("expiry", new InventoryReports.Expiry(sales, catalog, 14), Role.CASHIER);
        register("returns", new ReturnsReport(sales, Repositories.returns()), Role.MANAGER);
        register("staff", new StaffPerformanceReport(sales), Role.MANAGER);
        register("profit", new ProfitReport(sales), Role.MANAGER);
        register("vat", new VatSummaryReport(sales), Role.MANAGER);
    }

    private void register(String key, AbstractReportGenerator generator, Role minimumRole) {
        reports.put(key, generator);
        requiredRole.put(key, minimumRole);
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> report(@PathVariable String key,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) String format) {
        AbstractReportGenerator generator = reports.get(key);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown report: " + key + " (have: " + reports.keySet() + ")");
        }
        RoleGate.requireAtLeast(requiredRole.getOrDefault(key, Role.MANAGER));
        LocalDate fromDate = from == null || from.isBlank() ? null : LocalDate.parse(from);
        LocalDate toDate = to == null || to.isBlank() ? null : LocalDate.parse(to);
        ReportResult result = generator.generate(fromDate, toDate);
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("Content-Disposition",
                            "attachment; filename=martflow-" + key + ".csv")
                    .body(ReportCsvExporter.csv(result));
        }
        return ResponseEntity.ok(result);
    }

    /** Today's KPIs for the dashboard tiles (Asia/Dhaka day, VOIDED excluded). */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        RoleGate.requireAtLeast(Role.MANAGER);
        var start = TimeSource.startOfToday();
        var end = TimeSource.endOfToday();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal units = BigDecimal.ZERO;
        long bills = 0;
        for (Sale sale : Repositories.sales().findAll()) {
            if (sale.getStatus() == SaleStatus.VOIDED) {
                continue;
            }
            if (sale.getAt().isBefore(start) || !sale.getAt().isBefore(end)) {
                continue;
            }
            bills++;
            net = net.add(sale.getTotals().net());
            vat = vat.add(sale.getTotals().vat());
            for (var line : sale.getLines()) {
                if (line.productId() != null) {
                    units = units.add(line.quantity());
                }
            }
            for (var tender : sale.getTenders()) {
                if (tender.type() == com.martflow.payment.TenderType.CASH) {
                    cash = cash.add(tender.amount());
                }
            }
        }
        BigDecimal avgBasket = bills == 0 ? BigDecimal.ZERO
                : net.divide(BigDecimal.valueOf(bills), 2, RoundingMode.HALF_UP);

        int lowStock = 0;
        ProductIterator lowIterator = InventoryCatalog.getInstance().browseLowStock();
        while (lowIterator.hasNext()) {
            lowIterator.next();
            lowStock++;
        }
        int expiring = 0;
        ProductIterator expiryIterator = InventoryCatalog.getInstance().browseExpiring(14);
        while (expiryIterator.hasNext()) {
            expiryIterator.next();
            expiring++;
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("date", TimeSource.today().toString());
        kpis.put("bills", bills);
        kpis.put("netSales", net);
        kpis.put("vat", vat);
        kpis.put("cashTendered", cash);
        kpis.put("unitsSold", units);
        kpis.put("avgBasket", avgBasket);
        kpis.put("lowStockCount", lowStock);
        kpis.put("expiringCount", expiring);
        return kpis;
    }
}
