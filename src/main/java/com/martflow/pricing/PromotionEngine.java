package com.martflow.pricing;

import com.martflow.billing.item.BillableItem;
import com.martflow.common.TimeSource;
import com.martflow.loyalty.Customer;
import com.martflow.persistence.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves which {@link PricingStrategy} wins for each line, and validates coupons. The engine
 * reads the promotion table fresh on every bill, so a manager switching a sale on or off takes
 * effect at the very next scan — no code changes, which is exactly the Strategy payoff.
 */
public class PromotionEngine {

    private final Repository<Promotion> promotions;

    public PromotionEngine(Repository<Promotion> promotions) {
        this.promotions = promotions;
    }

    /** Applies the best active promotion to one raw line. */
    public BillableItem decorateLine(BillableItem line, Customer customer) {
        return strategyFor(line, customer).apply(line, new PricingContext(customer, TimeSource.today()));
    }

    /** Picks the winning strategy for a line: best category sale beats the member price. */
    public PricingStrategy strategyFor(BillableItem line, Customer customer) {
        LocalDate today = TimeSource.today();
        Optional<Promotion> bestSale = promotions.findAll().stream()
                .filter(p -> p.getType() == Promotion.Type.CATEGORY_SALE && p.isActiveOn(today))
                .filter(p -> p.getCategoryId() != null && p.getCategoryId().equals(line.categoryId()))
                .max(Comparator.comparing(p -> p.getPercentOff() == null ? BigDecimal.ZERO : p.getPercentOff()));
        if (bestSale.isPresent()) {
            Promotion sale = bestSale.get();
            return new CategorySale(sale.getCategoryId(), sale.getPercentOff(), sale.getName());
        }
        boolean member = customer != null && promotions.findAll().stream()
                .anyMatch(p -> p.getType() == Promotion.Type.MEMBER_PRICE && p.isActiveOn(today));
        if (member) {
            BigDecimal percent = promotions.findAll().stream()
                    .filter(p -> p.getType() == Promotion.Type.MEMBER_PRICE && p.isActiveOn(today))
                    .map(Promotion::getPercentOff)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(new BigDecimal("5"));
            return new MemberPrice(percent);
        }
        return new RegularPrice();
    }

    /**
     * Validates a coupon code against the promotion table and returns the amount it takes off a
     * bill of {@code netTotal}. Throws {@code IllegalArgumentException} for unknown/expired
     * codes so the cashier sees the problem immediately.
     */
    public BigDecimal couponAmount(String code, BigDecimal netTotal) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = code.trim().toUpperCase();
        LocalDate today = TimeSource.today();
        Optional<Promotion> found = promotions.findAll().stream()
                .filter(p -> p.getType() == Promotion.Type.COUPON_FLAT
                        || p.getType() == Promotion.Type.COUPON_PERCENT)
                .filter(p -> normalized.equals(p.getCode()))
                .findFirst();
        if (found.isEmpty()) {
            throw new IllegalArgumentException("Unknown coupon code: " + normalized);
        }
        Promotion coupon = found.get();
        if (!coupon.isActiveOn(today)) {
            throw new IllegalArgumentException("Coupon " + normalized + " is not active today");
        }
        BigDecimal amount = coupon.getType() == Promotion.Type.COUPON_FLAT
                ? (coupon.getFlatAmount() == null ? BigDecimal.ZERO : coupon.getFlatAmount())
                : netTotal.multiply((coupon.getPercentOff() == null ? BigDecimal.ZERO : coupon.getPercentOff())
                        .divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
        BigDecimal capped = amount.min(netTotal); // never pay the customer to shop
        return capped.max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Admin list of promotions. */
    public java.util.List<Promotion> all() {
        return promotions.findAll();
    }

    /** Admin upsert. */
    public Promotion save(Promotion promotion) {
        return promotions.save(promotion);
    }

    public void delete(String id) {
        promotions.delete(id);
    }
}
