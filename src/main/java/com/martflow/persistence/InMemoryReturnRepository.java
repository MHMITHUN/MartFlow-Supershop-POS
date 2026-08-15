package com.martflow.persistence;

import com.martflow.returns.SaleReturn;

/** In-memory return storage fallback. */
public class InMemoryReturnRepository extends InMemoryRepository<SaleReturn> {

    public InMemoryReturnRepository() {
        super(SaleReturn::getId);
    }
}
