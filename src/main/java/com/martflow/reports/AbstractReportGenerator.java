package com.martflow.reports;

import com.martflow.common.TimeSource;
import com.martflow.persistence.Repository;
import com.martflow.sales.Sale;
import com.martflow.sales.SaleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Pattern: Template Method.</b> The skeleton of every report is fixed here — fetch the sales
 * window (VOIDED excluded), aggregate into rows, wrap into a uniform result. A new report is
 * three small hooks ({@code name}, {@code headers}, {@code aggregate}) and zero control-flow
 * duplication; the reporting discipline (which sales count, how the window is bounded, what the
 * CSV looks like) is decided exactly once.
 */
public abstract class AbstractReportGenerator {

    private final Repository<Sale> sales;

    protected AbstractReportGenerator(Repository<Sale> sales) {
        this.sales = sales;
    }

    public abstract String name();

    /** The report's column headers. */
    protected abstract List<String> headers();

    /** Turns the fetched sales into rows. */
    protected abstract List<List<String>> aggregate(List<Sale> sales);

    /** Optional footnotes (range, totals). */
    protected java.util.Map<String, String> meta(List<Sale> sales) {
        return java.util.Map.of();
    }

    /** Hook-order trail — lets tests prove the template runs fetch -> aggregate -> build. */
    protected final List<String> trail = new ArrayList<>();

    public final ReportResult generate(LocalDate from, LocalDate to) {
        trail.clear();
        trail.add("fetch");
        LocalDate start = from == null ? TimeSource.today().minusDays(30) : from;
        LocalDate endInclusive = to == null ? TimeSource.today() : to;
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endExclusive = endInclusive.plusDays(1).atStartOfDay();
        List<Sale> window = new ArrayList<>();
        for (Sale sale : sales.findAll()) {
            if (sale.getStatus() == SaleStatus.VOIDED) {
                continue; // voided receipts are not revenue, ever
            }
            if (!sale.getAt().isBefore(startAt) && sale.getAt().isBefore(endExclusive)) {
                window.add(sale);
            }
        }
        trail.add("aggregate");
        List<List<String>> rows = aggregate(window);
        trail.add("build");
        return new ReportResult(name(), headers(), rows,
                meta(java.util.List.copyOf(window)));
    }
}
