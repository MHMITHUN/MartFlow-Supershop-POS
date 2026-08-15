package com.martflow.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Shared in-memory backing store for repositories. The two concrete fallbacks
 * ({@code InMemoryProductRepository}, {@code InMemoryOrderRepository}) only supply a key extractor.
 */
public abstract class InMemoryRepository<T> implements Repository<T> {

    private final Map<String, T> store = new ConcurrentHashMap<>();
    private final Function<T, String> keyExtractor;

    protected InMemoryRepository(Function<T, String> keyExtractor) {
        this.keyExtractor = keyExtractor;
    }

    @Override
    public Optional<T> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public T save(T entity) {
        store.put(keyExtractor.apply(entity), entity);
        return entity;
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
