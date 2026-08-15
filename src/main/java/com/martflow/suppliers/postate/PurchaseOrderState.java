package com.martflow.suppliers.postate;

/**
 * <b>Pattern: State.</b> The lifecycle of a purchase order, guarded by the state objects
 * themselves — a draft cannot be received, a closed PO cannot be cancelled, and the rules live
 * here rather than in if-chains scattered over the service.
 *
 * <p>Unlike a POS sale (plain enum — no long-lived per-status behaviour), a PO sits for days in
 * states with genuinely different allowed operations: draft (editable), ordered (awaiting the
 * truck), partially received (short delivery), received (pay and close). That asymmetry is
 * where the State pattern pays for itself.
 *
 * <p>{@link #fromName} is lenient — an unknown/hand-edited status string degrades to DRAFT
 * instead of 400-ing an entire PO list.
 */
public abstract class PurchaseOrderState {

    public static final PurchaseOrderState DRAFT = new States.DraftState();
    public static final PurchaseOrderState ORDERED = new States.OrderedState();
    public static final PurchaseOrderState PARTIALLY_RECEIVED = new States.PartiallyReceivedState();
    public static final PurchaseOrderState RECEIVED = new States.ReceivedState();
    public static final PurchaseOrderState CLOSED = new States.ClosedState();
    public static final PurchaseOrderState CANCELLED = new States.CancelledState();

    public abstract String name();

    public boolean canSubmit() {
        return false;
    }

    public boolean canReceive() {
        return false;
    }

    public boolean canClose() {
        return false;
    }

    public boolean canCancel() {
        return false;
    }

    public boolean isTerminal() {
        return false;
    }

    /** Lenient parse: unknown values fall back to DRAFT so list views never break. */
    public static PurchaseOrderState fromName(String name) {
        if (name == null) {
            return DRAFT;
        }
        return switch (name.trim().toUpperCase()) {
            case "ORDERED" -> ORDERED;
            case "PARTIALLY_RECEIVED" -> PARTIALLY_RECEIVED;
            case "RECEIVED" -> RECEIVED;
            case "CLOSED" -> CLOSED;
            case "CANCELLED" -> CANCELLED;
            default -> DRAFT;
        };
    }
}
