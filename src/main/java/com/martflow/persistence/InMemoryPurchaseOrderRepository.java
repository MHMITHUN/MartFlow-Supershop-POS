package com.martflow.persistence;

import com.martflow.suppliers.PurchaseOrder;

/** In-memory purchase-order storage fallback. */
public class InMemoryPurchaseOrderRepository extends InMemoryRepository<PurchaseOrder> {

    public InMemoryPurchaseOrderRepository() {
        super(PurchaseOrder::getPoNo);
    }
}
