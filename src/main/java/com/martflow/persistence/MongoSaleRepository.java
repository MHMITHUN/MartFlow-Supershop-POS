package com.martflow.persistence;

import com.martflow.sales.Sale;

/** Mongo-backed sale storage (embedded line snapshots — no joins needed). */
public class MongoSaleRepository extends MongoRepository<Sale> {

    public MongoSaleRepository() {
        super("sales", new SaleMapper());
    }
}
