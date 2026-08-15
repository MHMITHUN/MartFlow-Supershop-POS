package com.martflow.reports;

import java.util.List;
import java.util.Map;

/**
 * The uniform report shape every generator returns: a title, column headers, string rows (so
 * JSON and CSV render identically) and small meta (range, totals footnotes).
 */
public record ReportResult(
        String title,
        List<String> headers,
        List<List<String>> rows,
        Map<String, String> meta) {

    public ReportResult {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }
}
