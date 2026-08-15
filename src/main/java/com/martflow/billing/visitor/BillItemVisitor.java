package com.martflow.billing.visitor;

import com.martflow.billing.item.AdjustmentLine;
import com.martflow.billing.item.ComboLine;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.billing.decorator.CarryBagFee;
import com.martflow.billing.decorator.DeliveryFee;
import com.martflow.billing.decorator.LineDiscount;
import com.martflow.billing.decorator.RoundOffAdjustment;

/**
 * <b>Pattern: Visitor.</b> One traversal over a bill's item chains, four concrete behaviours:
 * VAT back-calculation (NBR filing), profit margin, thermal-receipt formatting — and any future
 * report. Double dispatch through {@code accept} means each item (and each decorator layer)
 * announces its exact type, so no instanceof chains anywhere.
 */
public interface BillItemVisitor {

    void visit(UnitLine line);

    void visit(WeighedLine line);

    void visit(ComboLine line);

    void visit(AdjustmentLine line);

    void visit(LineDiscount discount);

    void visit(CarryBagFee carryBag);

    void visit(DeliveryFee delivery);

    void visit(RoundOffAdjustment roundOff);
}
