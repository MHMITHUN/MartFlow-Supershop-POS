package com.martflow.persistence;

import com.martflow.catalog.Product;

/**
 * In-memory fallback for the product repository (no MONGODB_URI configured / Atlas unreachable).
 * Data lives only for the process lifetime — fine for demos and tests, not for production.
 */
public class InMemoryProductRepository extends InMemoryRepository<Product> {

    public InMemoryProductRepository() {
        super(Product::getId);
    }
}
