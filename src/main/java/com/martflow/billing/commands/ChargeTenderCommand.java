package com.martflow.billing.commands;

import com.martflow.payment.PaymentChannel;
import com.martflow.payment.PaymentResult;
import com.martflow.sales.Tender;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * Charges one tender through its {@link PaymentChannel} adapter, recording the transaction into
 * the sale on success. Undo refunds through the same channel.
 */
public final class ChargeTenderCommand implements BillingCommand {

    private final PaymentChannel channel;
    private final BigDecimal amount;
    private final String reference;
    private final Consumer<Tender> onCharged;

    public ChargeTenderCommand(PaymentChannel channel, BigDecimal amount, String reference,
                               Consumer<Tender> onCharged) {
        this.channel = channel;
        this.amount = amount;
        this.reference = reference;
        this.onCharged = onCharged;
    }

    @Override
    public void execute() {
        PaymentResult result = channel.charge(amount, reference);
        if (!result.success()) {
            throw new IllegalStateException(channel.type() + " tender failed: " + result.message());
        }
        onCharged.accept(new Tender(channel.type(), amount, result.transactionId(), reference));
    }

    @Override
    public void undo() {
        channel.refund(amount, reference);
    }
}
