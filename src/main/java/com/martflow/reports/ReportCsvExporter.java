package com.martflow.reports;

import java.util.ArrayList;
import java.util.List;

/** RFC4180 CSV rendering of any {@link ReportResult} — quotes fields containing separators. */
public final class ReportCsvExporter {

    private ReportCsvExporter() {
    }

    public static String csv(ReportResult report) {
        List<String> out = new ArrayList<>();
        out.add(row(report.headers()));
        for (List<String> line : report.rows()) {
            out.add(row(line));
        }
        return String.join("\r\n", out) + "\r\n";
    }

    private static String row(List<String> fields) {
        List<String> quoted = new ArrayList<>();
        for (String field : fields) {
            String safe = field == null ? "" : field;
            if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
                safe = "\"" + safe.replace("\"", "\"\"") + "\"";
            }
            quoted.add(safe);
        }
        return String.join(",", quoted);
    }
}
