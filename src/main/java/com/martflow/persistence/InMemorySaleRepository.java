package com.martflow.persistence;

import com.martflow.sales.Sale;

/** In-memory sale storage fallback. */
public class InMemorySaleRepository extends InMemoryRepository<Sale> {

    public InMemorySaleRepository() {
        super(Sale::getReceiptNo);
    }
}
