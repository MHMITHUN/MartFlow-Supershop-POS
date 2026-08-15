package com.martflow.reports;

import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per-cashier performance: bills rung, units sold, turnover and average basket. */
public final class StaffPerformanceReport extends AbstractReportGenerator {

    public StaffPerformanceReport(Repository<Sale> sales) {
        super(sales);
    }

    @Override
    public String name() {
        return "Staff Performance";
    }

    @Override
    protected List<String> headers() {
        return List.of("Cashier", "Bills", "Units", "Turnover", "Avg Basket");
    }

    @Override
    protected List<List<String>> aggregate(List<Sale> sales) {
        record Stats(long bills, BigDecimal units, BigDecimal turnover) {
        }
        Map<String, Stats> byCashier = new TreeMap<>();
        for (Sale sale : sales) {
            Stats current = byCashier.getOrDefault(sale.getCashierUsername(),
                    new Stats(0, BigDecimal.ZERO, BigDecimal.ZERO));
            BigDecimal units = BigDecimal.ZERO;
            for (SaleLine line : sale.getLines()) {
                if (line.productId() != null) {
                    units = units.add(line.quantity()); // units, not line count
                }
            }
            byCashier.put(sale.getCashierUsername(), new Stats(
                    current.bills() + 1,
                    current.units().add(units),
                    current.turnover().add(sale.getTotals().net())));
        }
        List<List<String>> rows = new ArrayList<>();
        for (var entry : byCashier.entrySet()) {
            Stats stats = entry.getValue();
            String avg = stats.bills() == 0 ? "0.00"
                    : stats.turnover().divide(BigDecimal.valueOf(stats.bills()), 2, RoundingMode.HALF_UP)
                            .toPlainString();
            rows.add(List.of(
                    entry.getKey(),
                    String.valueOf(stats.bills()),
                    stats.units().stripTrailingZeros().toPlainString(),
                    stats.turnover().toPlainString(),
                    avg));
        }
        return rows;
    }
}
