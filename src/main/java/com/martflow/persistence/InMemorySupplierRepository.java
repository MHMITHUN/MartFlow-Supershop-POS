package com.martflow.persistence;

import com.martflow.suppliers.Supplier;

/** In-memory supplier storage fallback. */
public class InMemorySupplierRepository extends InMemoryRepository<Supplier> {

    public InMemorySupplierRepository() {
        super(Supplier::getId);
    }
}
