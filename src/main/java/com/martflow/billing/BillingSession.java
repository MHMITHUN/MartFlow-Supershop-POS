package com.martflow.billing;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One cashier's in-progress bill plus its undo stack (capped at 10 snapshots — plenty for
 * "undo the last few mis-scans" without unbounded memory). Sessions are per login token, which
 * is what fixes the storefront's one-global-cart bug: two tills can never share a bill again.
 */
public class BillingSession {

    private static final int MAX_UNDO = 10;

    private final Bill bill = new Bill();
    private final Deque<BillMemento> undoStack = new ArrayDeque<>();
    private long lastAccessMs;

    public BillingSession(long nowMs) {
        this.lastAccessMs = nowMs;
    }

    public Bill bill() {
        return bill;
    }

    /** Snapshots the bill before a destructive edit (Memento caretaker duty). */
    public void snapshot() {
        undoStack.addFirst(BillMemento.of(bill));
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
    }

    /** Restores the most recent snapshot; {@code false} when there is nothing to undo. */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        undoStack.removeFirst().restoreInto(bill);
        return true;
    }

    public int undoDepth() {
        return undoStack.size();
    }

    /** Empties the bill after a completed tender (undo history goes with it). */
    public void resetAfterSale() {
        bill.clear();
        undoStack.clear();
    }

    public void touch(long nowMs) {
        lastAccessMs = nowMs;
    }

    public long lastAccessMs() {
        return lastAccessMs;
    }
}
