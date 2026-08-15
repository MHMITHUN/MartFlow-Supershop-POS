package com.martflow.persistence;

import com.martflow.pricing.Promotion;

/** In-memory promotion storage fallback. */
public class InMemoryPromotionRepository extends InMemoryRepository<Promotion> {

    public InMemoryPromotionRepository() {
        super(Promotion::getId);
    }
}
