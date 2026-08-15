package com.martflow.payment;

import com.martflow.payment.vendor.CardTerminalApi;

import java.math.BigDecimal;

/**
 * <b>Pattern: Adapter.</b> Adapts the legacy card-terminal SDK (doubles, int return codes) to
 * {@link PaymentChannel}. Amounts are rounded to 2dp BEFORE the double conversion so the
 * terminal never sees binary artefacts.
 */
public final class CardAdapter implements PaymentChannel {

    private final CardTerminalApi terminal = new CardTerminalApi();

    @Override
    public TenderType type() {
        return TenderType.CARD;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String reference) {
        double taka = amount.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        int auth = terminal.authorizePayment(taka, reference);
        if (auth < 0) {
            return PaymentResult.failed("Card declined by terminal");
        }
        return PaymentResult.ok("CARD-" + auth, "Authorized at terminal");
    }

    @Override
    public PaymentResult refund(BigDecimal amount, String reference) {
        if (reference != null && reference.startsWith("CARD-")) {
            try {
                int auth = Integer.parseInt(reference.substring("CARD-".length()));
                if (terminal.voidAuthorization(auth)) {
                    return PaymentResult.ok(reference, "Authorization voided before settlement");
                }
            } catch (NumberFormatException notAnAuthCode) {
                // fall through to failed
            }
        }
        return PaymentResult.failed("Card refund needs the original authorization");
    }
}
