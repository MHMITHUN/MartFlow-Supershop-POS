package com.martflow.reports.visitor;

import com.martflow.billing.VatCalculator;
import com.martflow.billing.decorator.CarryBagFee;
import com.martflow.billing.decorator.DeliveryFee;
import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.decorator.RoundOffAdjustment;
import com.martflow.billing.item.AdjustmentLine;
import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.billing.visitor.BillItemVisitor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * <b>Pattern: Visitor.</b> Margin walk over one bill's lines: revenue net of VAT minus cost of
 * goods sold. The line snapshots carry the unit cost at sale time, so historical margins never
 * move when purchase prices change later.
 */
public final class ProfitVisitor implements BillItemVisitor {

    private BigDecimal revenue = BigDecimal.ZERO.setScale(2);
    private BigDecimal cost = BigDecimal.ZERO.setScale(2);

    @Override
    public void visit(UnitLine line) {
        merchandise(line);
    }

    @Override
    public void visit(WeighedLine line) {
        merchandise(line);
    }

    @Override
    public void visit(ComboLine line) {
        merchandise(line);
    }

    @Override
    public void visit(AdjustmentLine line) {
        // no cost side; revenue side is zero for bare anchors
    }

    @Override
    public void visit(LineDiscount discount) {
        // the priced chain's net already reflects the discount
    }

    @Override
    public void visit(CarryBagFee carryBag) {
        revenue = revenue.add(carryBag.fee()); // fee income, no COGS
    }

    @Override
    public void visit(DeliveryFee delivery) {
        revenue = revenue.add(delivery.fee());
    }

    @Override
    public void visit(RoundOffAdjustment roundOff) {
        revenue = revenue.add(roundOff.delta());
    }

    private void merchandise(BillableItem line) {
        if (line.unitCost() == null) {
            return;
        }
        BigDecimal net = line.lineNet();
        BigDecimal vat = VatCalculator.vatOf(net, line.vatRate());
        revenue = revenue.add(net.subtract(vat).setScale(2, RoundingMode.HALF_UP));
        cost = cost.add(line.unitCost().multiply(line.quantity()).setScale(2, RoundingMode.HALF_UP));
    }

    /** Revenue net of VAT (fee income included, VAT-bearing lines ex-VAT). */
    public BigDecimal revenue() {
        return revenue;
    }

    /** Cost of goods sold at sale-time costs. */
    public BigDecimal cost() {
        return cost;
    }

    public BigDecimal profit() {
        return revenue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
    }
}
