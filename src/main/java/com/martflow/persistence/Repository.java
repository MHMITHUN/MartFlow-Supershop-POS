package com.martflow.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Domain-owned, storage-agnostic repository contract.
 *
 * <p>The whole application depends on this interface, never on MongoDB. Concrete implementations
 * (Mongo or in-memory) adapt a storage technology to it — that adaptation is the Adapter pattern
 * (design D2).
 *
 * @param <T> the aggregate root type stored
 */
public interface Repository<T> {

    Optional<T> findById(String id);

    List<T> findAll();

    /** Saves (inserts or replaces by id) and returns the persisted entity. */
    T save(T entity);

    void delete(String id);
}
