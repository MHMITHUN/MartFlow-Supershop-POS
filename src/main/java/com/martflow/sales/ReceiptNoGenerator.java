package com.martflow.sales;

import com.martflow.common.TimeSource;
import com.martflow.persistence.Repository;

import java.time.format.DateTimeFormatter;

/**
 * Sequential receipt numbers per Dhaka business day: {@code MF-20260815-001}. The sequence is
 * seeded from the maximum already stored for today, so a restart (or two tills someday) never
 * reuses a number.
 */
public class ReceiptNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Repository<Sale> sales;

    public ReceiptNoGenerator(Repository<Sale> sales) {
        this.sales = sales;
    }

    public synchronized String next() {
        String prefix = "MF-" + TimeSource.today().format(DAY) + "-";
        int max = sales.findAll().stream()
                .map(Sale::getReceiptNo)
                .filter(no -> no != null && no.startsWith(prefix))
                .mapToInt(no -> {
                    try {
                        return Integer.parseInt(no.substring(prefix.length()));
                    } catch (NumberFormatException ignored) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
        return prefix + String.format("%03d", max + 1);
    }
}
