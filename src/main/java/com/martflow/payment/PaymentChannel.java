package com.martflow.payment;

import java.math.BigDecimal;

/**
 * <b>Pattern: target of the Adapter.</b> What the billing pipeline wants from money movement:
 * charge an amount against a tender, refund it when a sale is voided. Five channels implement
 * it — cash, card terminal, bKash, Nagad and loyalty points — each adapting a different vendor
 * world behind this one port.
 */
public interface PaymentChannel {

    TenderType type();

    PaymentResult charge(BigDecimal amount, String reference);

    PaymentResult refund(BigDecimal amount, String reference);
}
