package com.martflow.loyalty;

import com.martflow.persistence.Repository;

import java.util.Optional;

/** Customer storage with phone lookup (the till's customer key). */
public interface CustomerRepository extends Repository<Customer> {

    Optional<Customer> findByPhone(String phone);
}
