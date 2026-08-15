package com.martflow.billing.validation;

import com.martflow.billing.item.BillableItem;
import com.martflow.billing.item.UnitLine;
import com.martflow.billing.item.WeighedLine;
import com.martflow.billing.validation.ValidationDtos.BillingCheck;
import com.martflow.billing.validation.ValidationDtos.TenderRequest;
import com.martflow.billing.validation.ValidationDtos.ValidationHandler;
import com.martflow.billing.validation.ValidationDtos.ValidationResult;
import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The six billing rules, one class each — all tiny, all independently testable.
 */
public final class Handlers {

    private Handlers() {
    }

    /** A bill with no lines cannot be tendered. */
    public static final class EmptyBillHandler implements ValidationHandler {
        @Override
        public ValidationResult validate(BillingCheck check) {
            return check.bill().lineCount() == 0
                    ? ValidationResult.fail("The bill is empty — scan at least one item")
                    : ValidationResult.ok();
        }
    }

    /** Weighed lines need a sensible weight; piece lines a sane count. */
    public static final class WeighmentRequiredHandler implements ValidationHandler {
        private static final BigDecimal MIN_KG = new BigDecimal("0.010");
        private static final int MAX_PIECES = 999;

        @Override
        public ValidationResult validate(BillingCheck check) {
            for (BillableItem item : check.bill().items()) {
                if (item instanceof WeighedLine && item.quantity().compareTo(MIN_KG) < 0) {
                    return ValidationResult.fail(
                            item.name() + " needs a weight of at least " + MIN_KG + " kg");
                }
                if (item instanceof UnitLine && item.quantity().intValueExact() > MAX_PIECES) {
                    return ValidationResult.fail(
                            item.name() + ": too many pieces on one line (max " + MAX_PIECES + ")");
                }
            }
            return ValidationResult.ok();
        }
    }

    /** Stock must cover the whole bill — same-item lines summed, combos checked as combos. */
    public static final class StockAvailabilityHandler implements ValidationHandler {
        @Override
        public ValidationResult validate(BillingCheck check) {
            Map<String, BigDecimal> needed = new HashMap<>();
            for (BillableItem item : check.bill().items()) {
                if (item.productId() == null) {
                    continue;
                }
                needed.merge(item.productId(), item.quantity(), BigDecimal::add);
            }
            for (Map.Entry<String, BigDecimal> entry : needed.entrySet()) {
                var product = check.catalog().findById(entry.getKey());
                if (product.isEmpty()) {
                    return ValidationResult.fail("Unknown product on the bill: " + entry.getKey());
                }
                if (product.get().getStock().compareTo(entry.getValue()) < 0) {
                    return ValidationResult.fail("Insufficient stock for " + product.get().getName()
                            + ": have " + product.get().getStock().stripTrailingZeros().toPlainString()
                            + ", bill needs " + entry.getValue().stripTrailingZeros().toPlainString());
                }
            }
            return ValidationResult.ok();
        }
    }

    /** A coupon on the bill must exist and be active today. */
    public static final class PromotionEligibilityHandler implements ValidationHandler {
        @Override
        public ValidationResult validate(BillingCheck check) {
            String code = check.bill().couponCode();
            if (code == null || code.isBlank()) {
                return ValidationResult.ok();
            }
            try {
                check.engine().couponAmount(code, check.totals().gross());
                return ValidationResult.ok();
            } catch (IllegalArgumentException invalid) {
                return ValidationResult.fail(invalid.getMessage());
            }
        }
    }

    /** Paying with points requires an attached loyalty customer. */
    public static final class LoyaltyCardValidHandler implements ValidationHandler {
        @Override
        public ValidationResult validate(BillingCheck check) {
            for (TenderRequest tender : check.tenders()) {
                if ("POINTS".equalsIgnoreCase(tender.type()) && check.bill().customer() == null) {
                    return ValidationResult.fail(
                            "Points tender needs a loyalty customer attached to the bill");
                }
            }
            return ValidationResult.ok();
        }
    }

    /** Tendered money must cover the net total. */
    public static final class TenderSufficientHandler implements ValidationHandler {
        @Override
        public ValidationResult validate(BillingCheck check) {
            BigDecimal tendered = BigDecimal.ZERO;
            for (TenderRequest tender : check.tenders()) {
                if (tender.amount() == null || tender.amount().signum() <= 0) {
                    return ValidationResult.fail("Every tender needs a positive amount");
                }
                tendered = tendered.add(tender.amount());
            }
            if (!MoneyUtil.gte(tendered, check.totals().net())) {
                return ValidationResult.fail("Tendered " + tendered + " is short of the bill total "
                        + check.totals().net());
            }
            return ValidationResult.ok();
        }
    }
}
