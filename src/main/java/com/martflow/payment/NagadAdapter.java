package com.martflow.payment;

import com.martflow.payment.vendor.NagadMerchantApi;

import java.math.BigDecimal;
import java.util.Map;

/**
 * <b>Pattern: Adapter.</b> Adapts the Nagad merchant API (stringly-typed payload in, plain
 * string out) to {@link PaymentChannel}.
 */
public final class NagadAdapter implements PaymentChannel {

    private final NagadMerchantApi nagad = new NagadMerchantApi();

    @Override
    public TenderType type() {
        return TenderType.NAGAD;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        String taka = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String trx = nagad.initiatePayment(Map.of(
                "amount", taka,
                "merchantRef", reference == null ? "" : reference,
                "currency", "BDT"));
        if (trx.startsWith("TRX-NG-")) {
            return PaymentResult.ok(trx, "Nagad payment initiated");
        }
        return PaymentResult.failed(trx);
    }

    @Override
    public PaymentResult refund(BigDecimal amount, String reference) {
        String outcome = nagad.refund(reference, amount.doubleValue());
        return outcome.startsWith("REFUND-OK-")
                ? PaymentResult.ok(outcome, "Nagad refund processed")
                : PaymentResult.failed(outcome);
    }
}
