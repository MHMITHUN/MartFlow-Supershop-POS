package com.martflow.suppliers.postate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PO state machine: every legal/illegal transition, lenient parsing. */
class PurchaseOrderStateTest {

    @Test
    void draftCanBeSubmittedAndCancelledButNotReceived() {
        PurchaseOrderState draft = PurchaseOrderState.DRAFT;
        assertTrue(draft.canSubmit());
        assertTrue(draft.canCancel());
        assertFalse(draft.canReceive());
        assertFalse(draft.canClose());
        assertFalse(draft.isTerminal());
    }

    @Test
    void orderedCanBeReceivedAndCancelled() {
        PurchaseOrderState ordered = PurchaseOrderState.ORDERED;
        assertTrue(ordered.canReceive());
        assertTrue(ordered.canCancel());
        assertFalse(ordered.canSubmit());
    }

    @Test
    void partiallyReceivedKeepsReceiving() {
        PurchaseOrderState partial = PurchaseOrderState.PARTIALLY_RECEIVED;
        assertTrue(partial.canReceive());
        assertTrue(partial.canCancel());
        assertFalse(partial.canClose());
    }

    @Test
    void receivedCanBeClosedButNotCancelled() {
        PurchaseOrderState received = PurchaseOrderState.RECEIVED;
        assertTrue(received.canClose());
        assertFalse(received.canCancel());
        assertFalse(received.canReceive());
    }

    @Test
    void closedAndCancelledAreTerminal() {
        assertTrue(PurchaseOrderState.CLOSED.isTerminal());
        assertTrue(PurchaseOrderState.CANCELLED.isTerminal());
        assertFalse(PurchaseOrderState.CLOSED.canCancel());
        assertFalse(PurchaseOrderState.CANCELLED.canReceive());
    }

    @Test
    void fromNameIsLenientAndNeverThrows() {
        assertSame(PurchaseOrderState.DRAFT, PurchaseOrderState.fromName(null));
        assertSame(PurchaseOrderState.DRAFT, PurchaseOrderState.fromName(""));
        assertSame(PurchaseOrderState.DRAFT, PurchaseOrderState.fromName("hand-edited"));
        assertSame(PurchaseOrderState.DRAFT, PurchaseOrderState.fromName("  draft  "));
        assertSame(PurchaseOrderState.ORDERED, PurchaseOrderState.fromName("ordered"));
        assertSame(PurchaseOrderState.PARTIALLY_RECEIVED, PurchaseOrderState.fromName("Partially_Received"));
        assertSame(PurchaseOrderState.RECEIVED, PurchaseOrderState.fromName("RECEIVED"));
        assertSame(PurchaseOrderState.CLOSED, PurchaseOrderState.fromName("CLOSED"));
        assertSame(PurchaseOrderState.CANCELLED, PurchaseOrderState.fromName("CANCELLED"));
    }

    @Test
    void stateNamesRoundTrip() {
        for (PurchaseOrderState state : new PurchaseOrderState[]{
                PurchaseOrderState.DRAFT, PurchaseOrderState.ORDERED,
                PurchaseOrderState.PARTIALLY_RECEIVED, PurchaseOrderState.RECEIVED,
                PurchaseOrderState.CLOSED, PurchaseOrderState.CANCELLED}) {
            assertEquals(state, PurchaseOrderState.fromName(state.name()));
        }
    }
}
