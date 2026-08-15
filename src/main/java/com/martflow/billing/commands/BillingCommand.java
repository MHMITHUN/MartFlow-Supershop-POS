package com.martflow.billing.commands;

/**
 * <b>Pattern: Command.</b> One reversible step of the tender pipeline. Steps run through the
 * {@link BillingInvoker}; any failure undoes every executed step in reverse, so a declined card
 * can never leave stock consumed or points moved.
 */
public interface BillingCommand {

    void execute();

    void undo();
}
