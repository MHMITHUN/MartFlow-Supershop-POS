package com.martflow.payment;

/** Outcome of one charge/refund against a payment channel. */
public record PaymentResult(boolean success, String transactionId, String message) {

    public static PaymentResult ok(String transactionId, String message) {
        return new PaymentResult(true, transactionId, message);
    }

    public static PaymentResult failed(String message) {
        return new PaymentResult(false, null, message);
    }
}
