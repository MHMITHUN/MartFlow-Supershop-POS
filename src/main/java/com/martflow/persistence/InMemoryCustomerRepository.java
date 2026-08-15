package com.martflow.persistence;

import com.martflow.loyalty.Customer;
import com.martflow.loyalty.CustomerRepository;

import java.util.Optional;

/** In-memory customer storage fallback. */
public class InMemoryCustomerRepository extends InMemoryRepository<Customer>
        implements CustomerRepository {

    public InMemoryCustomerRepository() {
        super(Customer::getId);
    }

    @Override
    public Optional<Customer> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(c -> phone.equals(c.getPhone()))
                .findFirst();
    }
}
