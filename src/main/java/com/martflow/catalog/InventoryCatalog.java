package com.martflow.catalog;

import com.martflow.catalog.iter.ExpiringSoonIterator;
import com.martflow.catalog.iter.InStockIterator;
import com.martflow.catalog.iter.LowStockIterator;
import com.martflow.catalog.iter.PriceRangeIterator;
import com.martflow.catalog.iter.ProductIterator;
import com.martflow.common.TimeSource;
import com.martflow.persistence.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The store's inventory registry — the one in-memory picture of what is on the shelves.
 *
 * <p><b>Pattern: Singleton.</b> Exactly one catalog exists per process; two catalogs would mean
 * two stock truths and overselling. Reached via {@link #getInstance()} after
 * {@link #initialize(Repository)}. The other singleton in the project is
 * {@code DatabaseConnection}.
 */
public class InventoryCatalog {

    private static volatile InventoryCatalog instance;

    private final Repository<Product> repository;
    private final Map<String, Product> productsById = new LinkedHashMap<>();

    private InventoryCatalog(Repository<Product> repository) {
        this.repository = repository;
        reload();
    }

    /** Initializes the single catalog with the chosen repository. Called once at startup. */
    public static InventoryCatalog initialize(Repository<Product> repository) {
        synchronized (InventoryCatalog.class) {
            if (instance == null) {
                instance = new InventoryCatalog(repository);
            }
            return instance;
        }
    }

    public static InventoryCatalog getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "InventoryCatalog not initialized. Call initialize(repository) at startup.");
        }
        return instance;
    }

    /** Test-only hook to reset the singleton between tests. */
    public static void resetForTesting() {
        synchronized (InventoryCatalog.class) {
            instance = null;
        }
    }

    /** Reloads all products from the repository into memory. */
    public void reload() {
        productsById.clear();
        if (repository != null) {
            for (Product product : repository.findAll()) {
                productsById.put(product.getId(), product);
            }
        }
    }

    /** Adds a product and persists it. */
    public Product addProduct(Product product) {
        repository.save(product);
        productsById.put(product.getId(), product);
        return product;
    }

    /** Removes a product from the catalog and the repository. */
    public void removeProduct(String id) {
        repository.delete(id);
        productsById.remove(id);
    }

    /**
     * Re-caches and persists a mutated product (stock moves, price edits). The repository may be
     * a {@code RoleGuardProxy}, which enforces who may change what.
     */
    public Product replaceProduct(Product product) {
        return addProduct(product);
    }

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(productsById.get(id));
    }

    /** Barcode lookup — the till's primary key (barcode scanners type digits + Enter). */
    public Optional<Product> findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        return productsById.values().stream()
                .filter(p -> barcode.equals(p.getBarcode()))
                .findFirst();
    }

    public List<Product> getAll() {
        return new ArrayList<>(productsById.values());
    }

    public int size() {
        return productsById.size();
    }

    // ---- Iterator-based browses (server-side filtered views) ----

    /** <b>Pattern: Iterator.</b> Re-iterable cursor over items with stock on hand. */
    public ProductIterator browseInStock() {
        return new InStockIterator(getAll());
    }

    /** <b>Pattern: Iterator.</b> Re-iterable cursor over items at/below their reorder level. */
    public ProductIterator browseLowStock() {
        return new LowStockIterator(getAll());
    }

    /**
     * <b>Pattern: Iterator.</b> Re-iterable cursor over items with a batch expiring within
     * {@code days} days (Dhaka clock), already-expired batches included.
     */
    public ProductIterator browseExpiring(int days) {
        LocalDate cutoff = TimeSource.today().plusDays(days);
        return new ExpiringSoonIterator(getAll(), cutoff);
    }

    /** <b>Pattern: Iterator.</b> Re-iterable cursor over items priced at or below {@code maxPrice}. */
    public ProductIterator browseUnder(BigDecimal maxPrice) {
        return new PriceRangeIterator(getAll(), maxPrice);
    }
}
