package com.martflow.reports.visitor;

import com.martflow.billing.decorator.CarryBagFee;
import com.martflow.billing.decorator.DeliveryFee;
import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.decorator.RoundOffAdjustment;
import com.martflow.billing.item.AdjustmentLine;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.billing.visitor.BillItemVisitor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>Pattern: Visitor.</b> Formats one bill's lines into 38-column thermal-receipt text — one
 * row per line including every decorator layer, exactly what a printer would burn into paper.
 * Works on reconstructed history, so a reprint from five years ago is byte-identical.
 */
public final class ReceiptFormatterVisitor implements BillItemVisitor {

    private static final int WIDTH = 38;

    private final List<String> lines = new ArrayList<>();

    @Override
    public void visit(UnitLine line) {
        add(line.name(), line.lineNet());
    }

    @Override
    public void visit(WeighedLine line) {
        add(line.name() + " " + line.quantity().stripTrailingZeros().toPlainString() + "kg",
                line.lineNet());
    }

    @Override
    public void visit(ComboLine line) {
        add("[combo] " + line.name(), line.lineNet());
    }

    @Override
    public void visit(AdjustmentLine line) {
        add(line.name(), BigDecimal.ZERO);
    }

    @Override
    public void visit(LineDiscount discount) {
        add("  incl. -" + discount.percentOff().stripTrailingZeros().toPlainString() + "% promo",
                discount.savedAmount().negate());
    }

    @Override
    public void visit(CarryBagFee carryBag) {
        add("Carry Bag", carryBag.fee());
    }

    @Override
    public void visit(DeliveryFee delivery) {
        add("Home Delivery", delivery.fee());
    }

    @Override
    public void visit(RoundOffAdjustment roundOff) {
        add("Round Off", roundOff.delta());
    }

    private void add(String label, BigDecimal amount) {
        String amountText = (amount.signum() < 0 ? "-" : "") + amount.abs().toPlainString();
        int space = WIDTH - label.length() - amountText.length();
        if (space < 1) {
            label = label.substring(0, Math.max(1, WIDTH - amountText.length() - 1));
            space = WIDTH - label.length() - amountText.length();
        }
        lines.add(label + " ".repeat(Math.max(1, space)) + amountText);
    }

    public List<String> rows() {
        return List.copyOf(lines);
    }

    public String text() {
        return String.join("\n", lines);
    }
}
