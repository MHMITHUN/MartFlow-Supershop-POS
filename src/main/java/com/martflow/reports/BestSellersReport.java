package com.martflow.reports;

import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Top-moving items in the window — units sold (not line count) and net revenue, top 15. */
public final class BestSellersReport extends AbstractReportGenerator {

    public BestSellersReport(Repository<Sale> sales) {
        super(sales);
    }

    @Override
    public String name() {
        return "Best Sellers";
    }

    @Override
    protected List<String> headers() {
        return List.of("Product", "SKU", "Units Sold", "Net Revenue");
    }

    @Override
    protected List<List<String>> aggregate(List<Sale> sales) {
        record Totals(String sku, BigDecimal qty, BigDecimal net) {
        }
        Map<String, Totals> byProduct = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Sale sale : sales) {
            for (SaleLine line : sale.getLines()) {
                if (line.productId() == null) {
                    continue;
                }
                Totals current = byProduct.getOrDefault(line.productId(),
                        new Totals(line.sku(), BigDecimal.ZERO, BigDecimal.ZERO));
                byProduct.put(line.productId(), new Totals(current.sku(),
                        current.qty().add(line.quantity()), current.net().add(line.net())));
                names.putIfAbsent(line.productId(), line.name());
            }
        }
        List<Map.Entry<String, Totals>> sorted = new ArrayList<>(byProduct.entrySet());
        sorted.sort(Map.Entry.<String, Totals>comparingByValue(
                Comparator.comparing(Totals::qty)).reversed());
        List<List<String>> rows = new ArrayList<>();
        int rank = 0;
        for (var entry : sorted) {
            if (++rank > 15) {
                break;
            }
            rows.add(List.of(
                    names.get(entry.getKey()),
                    entry.getValue().sku() == null ? "" : entry.getValue().sku(),
                    entry.getValue().qty().stripTrailingZeros().toPlainString(),
                    entry.getValue().net().toPlainString()));
        }
        return rows;
    }
}
