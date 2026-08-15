package com.martflow.persistence;

import com.martflow.loyalty.Customer;
import com.martflow.loyalty.CustomerRepository;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.Optional;

/** Mongo-backed customer storage with phone lookup. */
public class MongoCustomerRepository extends MongoRepository<Customer> implements CustomerRepository {

    public MongoCustomerRepository() {
        super("customers", new CustomerMapper());
    }

    @Override
    public Optional<Customer> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        Document doc = collection.find(Filters.eq("phone", phone)).first();
        return doc == null ? Optional.empty() : Optional.of(mapper.fromDocument(doc));
    }
}
