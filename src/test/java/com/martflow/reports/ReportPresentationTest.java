package com.martflow.reports;

import com.martflow.billing.decorator.CarryBagFee;
import com.martflow.billing.item.AdjustmentLine;
import com.martflow.reports.visitor.ReceiptFormatterVisitor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CSV escaping and the thermal-receipt formatter. */
class ReportPresentationTest {

    @Test
    void csvEscapesQuotesAndCommas() {
        ReportResult report = new ReportResult("Test", List.of("Item", "Note"),
                List.of(List.of("Soap, 100g", "He said \"ok\""), List.of("Salt", "plain")),
                java.util.Map.of());
        String csv = ReportCsvExporter.csv(report);
        String[] lines = csv.split("\r\n");
        assertEquals("Item,Note", lines[0]);
        assertEquals("\"Soap, 100g\",\"He said \"\"ok\"\"\"", lines[1]);
        assertEquals("Salt,plain", lines[2]);
    }

    @Test
    void receiptFormatterAlignsColumns() {
        ReceiptFormatterVisitor visitor = new ReceiptFormatterVisitor();
        new AdjustmentLine("Charges").accept(visitor);
        new CarryBagFee(new AdjustmentLine("Charges"), new BigDecimal("5.00")).accept(visitor);
        String text = visitor.text();
        assertTrue(text.contains("Carry Bag"));
        assertTrue(text.endsWith("5.00"));
        for (String line : visitor.rows()) {
            assertTrue(line.length() <= 38, "receipt line too wide: " + line);
        }
    }
}
