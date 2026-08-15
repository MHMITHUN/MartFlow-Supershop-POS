package com.martflow.reports;

import com.martflow.persistence.Repository;
import com.martflow.reports.visitor.ProfitVisitor;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLineReconstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Margin per receipt (and window totals): revenue net of VAT vs cost of goods at sale-time
 * costs, via the {@link ProfitVisitor}. This is the report that tells the owner the shop's
 * actual health — turnover alone lies.
 */
public final class ProfitReport extends AbstractReportGenerator {

    public ProfitReport(Repository<Sale> sales) {
        super(sales);
    }

    @Override
    public String name() {
        return "Profit";
    }

    @Override
    protected List<String> headers() {
        return List.of("Receipt", "Cashier", "Revenue (ex VAT)", "COGS", "Profit", "Margin %");
    }

    @Override
    protected List<List<String>> aggregate(List<Sale> sales) {
        List<List<String>> rows = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Sale sale : sales) {
            ProfitVisitor visitor = new ProfitVisitor();
            for (var line : SaleLineReconstructor.rebuildAll(sale)) {
                line.accept(visitor);
            }
            revenue = revenue.add(visitor.revenue());
            cost = cost.add(visitor.cost());
            BigDecimal profit = visitor.profit();
            String margin = visitor.revenue().signum() == 0 ? "0.00"
                    : profit.multiply(new BigDecimal("100"))
                            .divide(visitor.revenue(), 2, java.math.RoundingMode.HALF_UP)
                            .toPlainString();
            rows.add(List.of(
                    sale.getReceiptNo(),
                    sale.getCashierUsername(),
                    visitor.revenue().toPlainString(),
                    visitor.cost().toPlainString(),
                    profit.toPlainString(),
                    margin));
        }
        return rows;
    }

    @Override
    protected Map<String, String> meta(List<Sale> sales) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Sale sale : sales) {
            ProfitVisitor visitor = new ProfitVisitor();
            for (var line : SaleLineReconstructor.rebuildAll(sale)) {
                line.accept(visitor);
            }
            revenue = revenue.add(visitor.revenue());
            cost = cost.add(visitor.cost());
        }
        return Map.of(
                "totalRevenue", revenue.toPlainString(),
                "totalCogs", cost.toPlainString(),
                "totalProfit", revenue.subtract(cost).toPlainString());
    }
}
