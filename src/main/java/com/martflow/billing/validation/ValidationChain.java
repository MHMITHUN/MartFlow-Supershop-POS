package com.martflow.billing.validation;

import com.martflow.billing.validation.ValidationDtos.BillingCheck;
import com.martflow.billing.validation.ValidationDtos.ValidationHandler;
import com.martflow.billing.validation.ValidationDtos.ValidationResult;

import java.util.List;

/**
 * <b>Pattern: Chain of Responsibility.</b> Ordered billing rules; the first failing handler
 * decides the error the cashier sees. Adding a rule (say, a credit-limit check) means adding a
 * handler to the list — no existing rule changes.
 */
public class ValidationChain {

    private final List<ValidationHandler> handlers;

    public ValidationChain(List<ValidationHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public ValidationResult validate(BillingCheck check) {
        for (ValidationHandler handler : handlers) {
            ValidationResult result = handler.validate(check);
            if (!result.passed()) {
                return result;
            }
        }
        return ValidationResult.ok();
    }
}
