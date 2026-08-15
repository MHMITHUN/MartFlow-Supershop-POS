package com.martflow.payment;

import com.martflow.payment.vendor.BkashMerchantApi;

import java.math.BigDecimal;

/**
 * <b>Pattern: Adapter.</b> Adapts the bKash merchant API (poisha longs, wallet-bound response
 * records) to {@link PaymentChannel}: taka {@code x 100} poisha in, refund by original trxId.
 */
public final class BkashAdapter implements PaymentChannel {

    private static final String MERCHANT_WALLET = "01700-000000 (MartFlow Demo Merchant)";

    private final BkashMerchantApi bkash = new BkashMerchantApi();

    @Override
    public TenderType type() {
        return TenderType.BKASH;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        long poisha = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        var response = bkash.createPayment(MERCHANT_WALLET, poisha, reference);
        return response.success()
                ? PaymentResult.ok(response.trxId(), response.message())
                : PaymentResult.failed(response.message());
    }

    @Override
    public PaymentResult refund(BigDecimal amount, String reference) {
        long poisha = amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        var response = bkash.refundPayment(reference, poisha);
        return response.success()
                ? PaymentResult.ok(response.trxId(), response.message())
                : PaymentResult.failed(response.message());
    }
}
