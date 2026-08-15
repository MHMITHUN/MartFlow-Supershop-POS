package com.martflow.billing;

import com.martflow.common.MoneyUtil;

import java.math.BigDecimal;

/**
 * The one VAT formula (inclusive pricing, NBR style): {@code vat = net x rate / (100 + rate)}.
 * Every consumer — bill totals, the VAT visitor, reports — goes through here so the math can
 * never drift between screens and filings.
 */
public final class VatCalculator {

    private VatCalculator() {
    }

    /** Output VAT contained in a VAT-inclusive net amount at {@code ratePercent}. */
    public static BigDecimal vatOf(BigDecimal netAmount, BigDecimal ratePercent) {
        if (netAmount == null || ratePercent == null || ratePercent.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal rate = ratePercent.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
        return MoneyUtil.round(netAmount.multiply(rate).divide(BigDecimal.ONE.add(rate),
                6, java.math.RoundingMode.HALF_UP));
    }
}
