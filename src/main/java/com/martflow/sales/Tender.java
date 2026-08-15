package com.martflow.sales;

import com.martflow.payment.TenderType;

import java.math.BigDecimal;

/** How much was paid through which channel (transaction id from the adapter when present). */
public record Tender(TenderType type, BigDecimal amount, String transactionId, String reference) {
}
