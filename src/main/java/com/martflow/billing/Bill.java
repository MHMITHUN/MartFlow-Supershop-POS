package com.martflow.billing;

import com.martflow.billing.item.BillableItem;
import com.martflow.loyalty.Customer;
import com.martflow.pricing.PromotionEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The bill being built at one till right now: raw scanned lines (promotions are applied when
 * totals are computed, so a sale switched on mid-bill takes effect immediately), the attached
 * loyalty customer, an optional coupon, and carry-bag/delivery charges.
 */
public class Bill {

    private final List<BillableItem> items = new ArrayList<>();
    private Customer customer;
    private String couponCode;
    private int carryBags;
    private BigDecimal carryBagUnitFee = new BigDecimal("5.00");
    private BigDecimal deliveryFee = BigDecimal.ZERO.setScale(2);

    public void addItem(BillableItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot add a null line");
        }
        items.add(item);
    }

    public void removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IllegalArgumentException("Invalid line index: " + index);
        }
        items.remove(index);
    }

    public void updateQuantity(int index, BigDecimal quantity) {
        if (index < 0 || index >= items.size()) {
            throw new IllegalArgumentException("Invalid line index: " + index);
        }
        BillableItem existing = items.get(index);
        items.set(index, Rescan.of(existing, quantity));
    }

    public void clear() {
        items.clear();
        customer = null;
        couponCode = null;
        carryBags = 0;
        deliveryFee = BigDecimal.ZERO.setScale(2);
    }

    public List<BillableItem> items() {
        return List.copyOf(items);
    }

    public int lineCount() {
        return items.size();
    }

    public Customer customer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String couponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public int carryBags() {
        return carryBags;
    }

    public void setCarryBags(int carryBags) {
        if (carryBags < 0) {
            throw new IllegalArgumentException("Carry bag count cannot be negative");
        }
        this.carryBags = carryBags;
    }

    public BigDecimal carryBagUnitFee() {
        return carryBagUnitFee;
    }

    public void setCarryBagUnitFee(BigDecimal fee) {
        this.carryBagUnitFee = fee == null ? BigDecimal.ZERO.setScale(2) : fee;
    }

    public BigDecimal deliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee == null ? BigDecimal.ZERO.setScale(2) : deliveryFee;
    }

    /**
     * Computes the bill's money picture through the promotion engine: decorated lines, savings,
     * coupon, fees, net and VAT. Round-off is a tender-time concern (cash only) and stays zero
     * here.
     */
    public Totals totals(PromotionEngine engine) {
        List<BillableItem> priced = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        for (BillableItem item : items) {
            gross = gross.add(item.lineNet());
            priced.add(engine.decorateLine(item, customer));
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BillableItem line : priced) {
            subtotal = subtotal.add(line.lineNet());
        }
        BigDecimal lineDiscount = gross.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);
        // An invalid/expired coupon contributes zero here; the validation chain is the layer
        // that rejects it with a cashier-readable message at tender time.
        BigDecimal coupon = BigDecimal.ZERO.setScale(2);
        if (couponCode != null && !couponCode.isBlank()) {
            try {
                coupon = engine.couponAmount(couponCode, subtotal);
            } catch (IllegalArgumentException invalid) {
                coupon = BigDecimal.ZERO.setScale(2);
            }
        }
        BigDecimal fees = carryBagUnitFee.multiply(BigDecimal.valueOf(carryBags))
                .add(deliveryFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = subtotal.subtract(coupon).add(fees).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = BigDecimal.ZERO;
        for (BillableItem line : priced) {
            vat = vat.add(VatCalculator.vatOf(line.lineNet(), line.vatRate()));
        }
        return new Totals(priced, gross, lineDiscount, coupon, fees, net, vat);
    }

    /** The bill's money picture — decorated lines plus every total the till displays. */
    public record Totals(
            List<BillableItem> pricedLines,
            BigDecimal gross,
            BigDecimal lineDiscount,
            BigDecimal coupon,
            BigDecimal fees,
            BigDecimal net,
            BigDecimal vat) {
    }
}
