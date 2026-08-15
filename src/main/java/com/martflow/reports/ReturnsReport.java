package com.martflow.reports;

import com.martflow.persistence.Repository;
import com.martflow.returns.ReturnLine;
import com.martflow.returns.SaleReturn;
import com.martflow.sales.Sale;

import java.util.ArrayList;
import java.util.List;

/** Returns log for the window: what came back, why, and how much was refunded. */
public final class ReturnsReport extends AbstractReportGenerator {

    private final Repository<SaleReturn> returns;

    public ReturnsReport(Repository<Sale> sales, Repository<SaleReturn> returns) {
        super(sales);
        this.returns = returns;
    }

    @Override
    public String name() {
        return "Returns & Refunds";
    }

    @Override
    protected List<String> headers() {
        return List.of("Date", "Receipt", "Items", "Refund", "Channel");
    }

    @Override
    protected List<List<String>> aggregate(List<Sale> sales) {
        List<List<String>> rows = new ArrayList<>();
        for (SaleReturn r : returns.findAll()) {
            StringBuilder items = new StringBuilder();
            for (ReturnLine line : r.getLines()) {
                if (items.length() > 0) {
                    items.append("; ");
                }
                items.append(line.name()).append(" x")
                        .append(line.quantity().stripTrailingZeros().toPlainString());
                if (line.reason() != null && !line.reason().isBlank()) {
                    items.append(" (").append(line.reason()).append(")");
                }
            }
            rows.add(List.of(
                    r.getAt().toLocalDate().toString(),
                    r.getReceiptNo(),
                    items.toString(),
                    r.getRefundAmount().toPlainString(),
                    r.getRefundChannel()));
        }
        return rows;
    }
}
