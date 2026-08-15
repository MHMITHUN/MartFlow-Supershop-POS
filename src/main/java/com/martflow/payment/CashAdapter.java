package com.martflow.payment;

import java.math.BigDecimal;

/** Cash tender: the drawer itself. Charging is acceptance; refunding is cash handed back. */
public final class CashAdapter implements PaymentChannel {

    @Override
    public TenderType type() {
        return TenderType.CASH;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        return PaymentResult.ok("CASH-" + reference, "Cash accepted");
    }

    @Override
    public PaymentResult refund(BigDecimal amount, String reference) {
        return PaymentResult.ok("CASH-REFUND-" + reference, "Cash returned from drawer");
    }
}
