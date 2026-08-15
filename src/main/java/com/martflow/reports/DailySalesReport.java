package com.martflow.reports;

import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One row per business day: bills, gross, discounts, coupons, net and VAT. */
public final class DailySalesReport extends AbstractReportGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DailySalesReport(Repository<Sale> sales) {
        super(sales);
    }

    @Override
    public String name() {
        return "Daily Sales";
    }

    @Override
    protected List<String> headers() {
        return List.of("Date", "Bills", "Gross", "Discount", "Coupon", "Fees", "Net", "VAT");
    }

    @Override
    protected List<List<String>> aggregate(List<Sale> sales) {
        // per day: [bills, gross, discount, coupon, fees, net, vat]
        Map<String, BigDecimal[]> byDay = new TreeMap<>();
        for (Sale sale : sales) {
            BigDecimal[] t = byDay.computeIfAbsent(sale.getAt().format(DAY),
                    d -> new BigDecimal[7]);
            for (int i = 0; i < 7; i++) {
                if (t[i] == null) {
                    t[i] = BigDecimal.ZERO;
                }
            }
            t[0] = t[0].add(BigDecimal.ONE);
            t[1] = t[1].add(sale.getTotals().gross());
            t[2] = t[2].add(sale.getTotals().discount());
            t[3] = t[3].add(sale.getTotals().coupon());
            t[4] = t[4].add(sale.getTotals().fees());
            t[5] = t[5].add(sale.getTotals().net());
            t[6] = t[6].add(sale.getTotals().vat());
        }
        List<List<String>> rows = new ArrayList<>();
        for (var entry : byDay.entrySet()) {
            List<String> row = new ArrayList<>();
            row.add(entry.getKey());
            for (BigDecimal value : entry.getValue()) {
                row.add(value.setScale(2).toPlainString());
            }
            rows.add(row);
        }
        return rows;
    }
}
