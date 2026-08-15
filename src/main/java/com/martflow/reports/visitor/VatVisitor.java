package com.martflow.reports.visitor;

import com.martflow.billing.VatCalculator;
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
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>Pattern: Visitor.</b> Walks one bill's lines and back-calculates the output VAT per NBR
 * rate slab — the numbers a supershop files monthly. Works identically on live bills and
 * Mongo-loaded sales (the lines are reconstructed first), so a reprint-time audit matches the
 * filed figures.
 */
public final class VatVisitor implements BillItemVisitor {

    private final Map<String, BigDecimal> taxableByRate = new LinkedHashMap<>(); // net-ex-VAT
    private final Map<String, BigDecimal> vatByRate = new LinkedHashMap<>();

    @Override
    public void visit(UnitLine line) {
        record(line.lineNet(), line.vatRate());
    }

    @Override
    public void visit(WeighedLine line) {
        record(line.lineNet(), line.vatRate());
    }

    @Override
    public void visit(ComboLine line) {
        record(line.lineNet(), line.vatRate());
    }

    @Override
    public void visit(AdjustmentLine line) {
        // anchors carry no amount by themselves
    }

    @Override
    public void visit(LineDiscount discount) {
        // the discounted net is what the priced chain reports — nothing extra to record
    }

    @Override
    public void visit(CarryBagFee carryBag) {
        // VAT-exempt fee
    }

    @Override
    public void visit(DeliveryFee delivery) {
        // VAT-exempt fee
    }

    @Override
    public void visit(RoundOffAdjustment roundOff) {
        // rounding is not consideration for goods
    }

    private void record(BigDecimal net, BigDecimal rate) {
        if (net.signum() <= 0) {
            return;
        }
        BigDecimal vat = VatCalculator.vatOf(net, rate);
        String key = rate.stripTrailingZeros().toPlainString();
        taxableByRate.merge(key, net.subtract(vat).setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        vatByRate.merge(key, vat, BigDecimal::add);
    }

    /** Output VAT per rate slab (key = percent, e.g. "15"). */
    public Map<String, BigDecimal> vatByRate() {
        return new LinkedHashMap<>(vatByRate);
    }

    /** VAT-exclusive taxable value per rate slab. */
    public Map<String, BigDecimal> taxableByRate() {
        return new LinkedHashMap<>(taxableByRate);
    }

    public BigDecimal totalVat() {
        return vatByRate.values().stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
