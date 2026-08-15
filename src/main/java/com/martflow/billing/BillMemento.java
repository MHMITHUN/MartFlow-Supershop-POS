package com.martflow.billing;

import com.martflow.billing.item.BillableItem;
import com.martflow.loyalty.Customer;

import java.math.BigDecimal;
import java.util.List;

/**
 * <b>Pattern: Memento.</b> An opaque snapshot of a bill (lines, customer, coupon, charges)
 * taken before every destructive edit. The cashier's Undo button walks a stack of these —
 * mis-scans are a per-minute event at a real till, so this is a money feature, not a gimmick.
 *
 * <p>Package-private internals: nobody outside {@code billing} can read or forge a snapshot.
 */
public final class BillMemento {

    private final List<BillableItem> items;
    private final Customer customer;
    private final String couponCode;
    private final int carryBags;
    private final BigDecimal carryBagUnitFee;
    private final BigDecimal deliveryFee;

    BillMemento(List<BillableItem> items, Customer customer, String couponCode,
                int carryBags, BigDecimal carryBagUnitFee, BigDecimal deliveryFee) {
        this.items = List.copyOf(items);
        this.customer = customer;
        this.couponCode = couponCode;
        this.carryBags = carryBags;
        this.carryBagUnitFee = carryBagUnitFee;
        this.deliveryFee = deliveryFee;
    }

    static BillMemento of(Bill bill) {
        return new BillMemento(bill.items(), bill.customer(), bill.couponCode(),
                bill.carryBags(), bill.carryBagUnitFee(), bill.deliveryFee());
    }

    void restoreInto(Bill bill) {
        bill.clear();
        for (BillableItem item : items) {
            bill.addItem(item);
        }
        bill.setCustomer(customer);
        bill.setCouponCode(couponCode);
        bill.setCarryBags(carryBags);
        bill.setCarryBagUnitFee(carryBagUnitFee);
        bill.setDeliveryFee(deliveryFee);
    }
}
