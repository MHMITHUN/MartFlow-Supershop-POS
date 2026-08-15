package com.martflow.payment;

import com.martflow.loyalty.Customer;
import com.martflow.loyalty.LoyaltyService;

import java.math.BigDecimal;
import java.util.function.Supplier;

/**
 * <b>Pattern: Adapter.</b> Turns loyalty points into a tender channel: 1 point redeems as
 * 1 BDT, balance checked at charge time, returned to the balance on refund. The customer is
 * resolved lazily per charge (the cashier attaches the customer to the bill before tender).
 */
public final class PointsAdapter implements PaymentChannel {

    private final LoyaltyService loyalty;
    private final Supplier<Customer> currentCustomer;

    public PointsAdapter(LoyaltyService loyalty, Supplier<Customer> currentCustomer) {
        this.loyalty = loyalty;
        this.currentCustomer = currentCustomer;
    }

    @Override
    public TenderType type() {
        return TenderType.POINTS;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        Customer customer = currentCustomer.get();
        if (customer == null) {
            return PaymentResult.failed("No loyalty customer attached to the bill");
        }
        int points = amount.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        try {
            loyalty.redeem(customer.getId(), points);
        } catch (RuntimeException shortBalance) {
            return PaymentResult.failed(shortBalance.getMessage());
        }
        return PaymentResult.ok("PTS-" + customer.getId() + "-" + points,
                points + " points redeemed by " + customer.getName());
    }

    @Override
    public PaymentResult refund(BigDecimal amount, String reference) {
        Customer customer = currentCustomer.get();
        if (customer == null) {
            return PaymentResult.failed("No loyalty customer to return points to");
        }
        int points = amount.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        loyalty.reverse(customer.getId(), points);
        return PaymentResult.ok("PTS-REFUND-" + points, points + " points returned");
    }
}
