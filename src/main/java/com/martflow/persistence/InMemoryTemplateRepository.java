package com.martflow.persistence;

import com.martflow.suppliers.StandingOrderTemplate;

/** In-memory standing-order-template storage fallback. */
public class InMemoryTemplateRepository extends InMemoryRepository<StandingOrderTemplate> {

    public InMemoryTemplateRepository() {
        super(StandingOrderTemplate::getId);
    }
}
