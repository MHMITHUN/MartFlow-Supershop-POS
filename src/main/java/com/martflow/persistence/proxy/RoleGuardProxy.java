package com.martflow.persistence.proxy;

import com.martflow.persistence.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * <b>Pattern: Protection Proxy.</b> Sits in front of a {@link Repository} and enforces who may
 * write what, reading the caller from {@code RoleContext}. Reads always pass through — guards
 * apply to mutations only.
 *
 * <p>The per-aggregate rules live in a {@link WritePolicy} so the proxy stays generic: products
 * use "any role may save stock-only changes, managers may change catalog data, admins delete";
 * users, promotions and purchase orders plug in their own policies in later phases.
 *
 * <p>Because the guard is at the data boundary, a cashier cannot mutate the catalog even through
 * a buggy controller — the proxy is the last line of defense.
 */
public final class RoleGuardProxy<T> implements Repository<T> {

    /** Per-aggregate write rules. Implementations throw {@code AccessDeniedException} to block. */
    public interface WritePolicy<T> {

        /** Guards {@code save}. {@code existing} is {@code null} for creates. */
        void checkSave(T existing, T candidate);

        /** Guards {@code delete}. */
        void checkDelete(String id);
    }

    private final Repository<T> delegate;
    private final Function<T, String> idOf;
    private final WritePolicy<T> policy;

    public RoleGuardProxy(Repository<T> delegate, Function<T, String> idOf, WritePolicy<T> policy) {
        this.delegate = delegate;
        this.idOf = idOf;
        this.policy = policy;
    }

    @Override
    public Optional<T> findById(String id) {
        return delegate.findById(id); // reads are transparent — that is the proxy point
    }

    @Override
    public List<T> findAll() {
        return delegate.findAll();
    }

    @Override
    public T save(T entity) {
        T existing = delegate.findById(idOf.apply(entity)).orElse(null);
        policy.checkSave(existing, entity);
        return delegate.save(entity);
    }

    @Override
    public void delete(String id) {
        policy.checkDelete(id);
        delegate.delete(id);
    }
}
