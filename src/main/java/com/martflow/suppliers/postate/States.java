package com.martflow.suppliers.postate;

/** The six concrete PO states (package file: they are one-line each). */
final class States {

    private States() {
    }

    static final class DraftState extends PurchaseOrderState {
        @Override
        public String name() {
            return "DRAFT";
        }

        @Override
        public boolean canSubmit() {
            return true;
        }

        @Override
        public boolean canCancel() {
            return true;
        }
    }

    static final class OrderedState extends PurchaseOrderState {
        @Override
        public String name() {
            return "ORDERED";
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public boolean canCancel() {
            return true;
        }
    }

    static final class PartiallyReceivedState extends PurchaseOrderState {
        @Override
        public String name() {
            return "PARTIALLY_RECEIVED";
        }

        @Override
        public boolean canReceive() {
            return true; // finish receiving the outstanding quantities
        }

        @Override
        public boolean canCancel() {
            return true; // cancel the unreceived remainder
        }
    }

    static final class ReceivedState extends PurchaseOrderState {
        @Override
        public String name() {
            return "RECEIVED";
        }

        @Override
        public boolean canClose() {
            return true;
        }
    }

    static final class ClosedState extends PurchaseOrderState {
        @Override
        public String name() {
            return "CLOSED";
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    static final class CancelledState extends PurchaseOrderState {
        @Override
        public String name() {
            return "CANCELLED";
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }
}
