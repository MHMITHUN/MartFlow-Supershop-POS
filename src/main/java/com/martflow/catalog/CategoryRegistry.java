package com.martflow.catalog;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The store's category & VAT policy. Categories are business policy (NBR VAT slabs), not
 * user-editable data, so they live in code rather than the database. Products reference a
 * category by id; the VAT rate is always resolved through here so a rate change is a one-line
 * policy edit.
 */
public final class CategoryRegistry {

    private static final Map<String, Category> BY_ID = new LinkedHashMap<>();

    static {
        register(new Category("staples", "Staples — Rice, Atta, Oil, Salt, Sugar", new BigDecimal("0")));
        register(new Category("fresh", "Fresh — Vegetables, Fish, Meat, Eggs", new BigDecimal("0")));
        register(new Category("grocery", "Grocery — Packaged & Spices", new BigDecimal("7.5")));
        register(new Category("dairy", "Dairy — Milk, Powder, Ghee", new BigDecimal("7.5")));
        register(new Category("beverages", "Beverages — Soft Drinks, Juice, Water", new BigDecimal("15")));
        register(new Category("snacks", "Snacks — Biscuits, Chanachur, Chocolate", new BigDecimal("15")));
        register(new Category("toiletries", "Toiletries — Soap, Toothpaste, Shampoo", new BigDecimal("15")));
        register(new Category("personal-care", "Personal Care — Cream, Oil, Hygiene", new BigDecimal("15")));
        register(new Category("household", "Household — Cleaners, Tissue, Tools", new BigDecimal("15")));
    }

    private CategoryRegistry() {
    }

    private static void register(Category category) {
        BY_ID.put(category.id(), category);
    }

    public static List<Category> all() {
        return List.copyOf(BY_ID.values());
    }

    public static Optional<Category> find(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }

    /** Resolves a category id, throwing for unknown ids (bad reference = programming/API error). */
    public static Category get(String id) {
        Category category = id == null ? null : BY_ID.get(id);
        if (category == null) {
            throw new IllegalArgumentException("Unknown category: " + id);
        }
        return category;
    }

    /** VAT rate (percent) of a category id; unknown ids are treated as 15% (fail-safe revenue side). */
    public static BigDecimal vatRateOf(String categoryId) {
        Category category = BY_ID.get(categoryId);
        return category == null ? new BigDecimal("15") : category.vatRatePercent();
    }
}
