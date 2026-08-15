package com.martflow.reports;

import com.martflow.persistence.Repository;
import com.martflow.reports.visitor.VatVisitor;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleLineReconstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Output VAT by NBR rate slab over the window — the monthly filing summary. Powered by the
 * {@link VatVisitor} walking reconstructed sale lines, so what the owner files is derived from
 * the exact same line math the receipts were printed with.
 */
public final class VatSummaryReport extends AbstractReportGenerator {

    public VatSummaryReport(Repository<Sale> sales) {
        super(sales);
    }

    @Override
    public String name() {
        return "VAT Summary (NBR)";
    }

    @Override
    protected List<String> headers() {
        return List.of("VAT Rate %", "Taxable (ex VAT)", "Output VAT");
    }

    @Override
    protected List<List<String>> aggregate(List<Sale> sales) {
        VatVisitor visitor = new VatVisitor();
        for (Sale sale : sales) {
            for (var line : SaleLineReconstructor.rebuildAll(sale)) {
                line.accept(visitor);
            }
        }
        List<List<String>> rows = new ArrayList<>();
        for (var entry : visitor.vatByRate().entrySet()) {
            rows.add(List.of(
                    entry.getKey(),
                    visitor.taxableByRate().getOrDefault(entry.getKey(), BigDecimal.ZERO)
                            .toPlainString(),
                    entry.getValue().toPlainString()));
        }
        return rows;
    }

    @Override
    protected Map<String, String> meta(List<Sale> sales) {
        VatVisitor visitor = new VatVisitor();
        for (Sale sale : sales) {
            for (var line : SaleLineReconstructor.rebuildAll(sale)) {
                line.accept(visitor);
            }
        }
        return Map.of("totalOutputVat", visitor.totalVat().toPlainString());
    }
}
