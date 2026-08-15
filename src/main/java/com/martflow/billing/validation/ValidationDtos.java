package com.martflow.billing.validation;

import java.math.BigDecimal;
import java.util.List;

/** Shared types of the billing validation chain. */
public final class ValidationDtos {

    private ValidationDtos() {
    }

    /** One tender the cashier is about to take. */
    public record TenderRequest(String type, BigDecimal amount, String reference) {
    }

    /** What the validation chain inspects: the bill, its computed totals and the tenders. */
    public record BillingCheck(com.martflow.billing.Bill bill,
                               com.martflow.billing.Bill.Totals totals,
                               List<TenderRequest> tenders,
                               com.martflow.catalog.InventoryCatalog catalog,
                               com.martflow.pricing.PromotionEngine engine) {
    }

    /** Chain verdict: first failure short-circuits. */
    public record ValidationResult(boolean passed, String failure) {

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }

    /** Handler contract (Chain of Responsibility). */
    public interface ValidationHandler {

        ValidationResult validate(BillingCheck check);
    }
}
