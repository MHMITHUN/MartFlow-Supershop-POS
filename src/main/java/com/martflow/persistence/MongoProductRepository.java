package com.martflow.persistence;

import com.martflow.catalog.Batch;
import com.martflow.catalog.ComboProduct;
import com.martflow.catalog.Product;
import com.martflow.catalog.ProductUnit;
import com.martflow.catalog.UnitProduct;
import com.martflow.catalog.WeighedProduct;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Adapts the MongoDB driver to the domain {@link Repository<Product>} interface — the Adapter
 * pattern. The rest of the app never sees a Mongo {@code Document}.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>all money/stock values persist as exact decimal strings (never doubles);</li>
 *   <li>batch dates persist as ISO {@code yyyy-MM-dd} strings;</li>
 *   <li>a {@link ComboProduct} persists its component ids and is reconstructed via a two-pass
 *       load (leaves first, then combos resolved against the leaf map);</li>
 *   <li>combos with missing components are skipped rather than crashing the load.</li>
 * </ul>
 */
public class MongoProductRepository implements Repository<Product> {

    private final MongoCollection<Document> collection;

    public MongoProductRepository() {
        this.collection = DatabaseConnection.getDatabase().getCollection("products");
    }

    @Override
    public Optional<Product> findById(String id) {
        Document doc = collection.find(Filters.eq("_id", id)).first();
        if (doc == null) {
            return Optional.empty();
        }
        if ("COMBO".equals(doc.getString("type"))) {
            return Optional.ofNullable(toCombo(doc, cid -> findById(cid).orElse(null)));
        }
        return Optional.of(toLeaf(doc));
    }

    @Override
    public List<Product> findAll() {
        // Two-pass load: leaves first so combos can resolve their components from the map.
        Map<String, Product> byId = new LinkedHashMap<>();
        List<Document> comboDocs = new ArrayList<>();
        for (Document doc : collection.find()) {
            if ("COMBO".equals(doc.getString("type"))) {
                comboDocs.add(doc);
            } else {
                Product leaf = toLeaf(doc);
                byId.put(leaf.getId(), leaf);
            }
        }
        for (Document doc : comboDocs) {
            Product combo = toCombo(doc, byId::get);
            if (combo != null) {
                byId.put(combo.getId(), combo);
            }
        }
        return new ArrayList<>(byId.values());
    }

    @Override
    public Product save(Product product) {
        collection.replaceOne(
                Filters.eq("_id", product.getId()),
                toDocument(product),
                new ReplaceOptions().upsert(true));
        return product;
    }

    @Override
    public void delete(String id) {
        collection.deleteOne(Filters.eq("_id", id));
    }

    // ---- to domain ----

    private static Product toLeaf(Document d) {
        String type = d.getString("type");
        String id = d.getString("_id");
        Product product;
        if ("WEIGHED".equals(type)) {
            product = new WeighedProduct(
                    id,
                    d.getString("sku"),
                    d.getString("barcode"),
                    d.getString("name"),
                    d.getString("description"),
                    d.getString("categoryId"),
                    d.getString("supplierId"),
                    unit(d.getString("unit"), ProductUnit.KG),
                    money(d.getString("costPrice")),
                    money(d.getString("pricePerUnit")),
                    qty(d.getString("stock")),
                    d.getInteger("reorderLevel", 0));
        } else {
            product = new UnitProduct(
                    id,
                    d.getString("sku"),
                    d.getString("barcode"),
                    d.getString("name"),
                    d.getString("description"),
                    d.getString("categoryId"),
                    d.getString("supplierId"),
                    unit(d.getString("unit"), ProductUnit.PIECE),
                    money(d.getString("costPrice")),
                    money(d.getString("mrp")),
                    qty(d.getString("stock")),
                    d.getInteger("reorderLevel", 0));
        }
        loadBatches(d, product);
        return product;
    }

    /** Replays persisted batches onto a loaded product (batch records only — stock is already set). */
    private static void loadBatches(Document d, Product product) {
        List<Document> batchDocs = d.getList("batches", Document.class);
        if (batchDocs == null) {
            return;
        }
        for (Document bd : batchDocs) {
            String expiry = bd.getString("expiry");
            product.addBatch(new Batch(bd.getString("batchNo"),
                    expiry == null || expiry.isBlank() ? null : LocalDate.parse(expiry),
                    qty(bd.getString("receivedQty"))));
        }
    }

    private static ComboProduct toCombo(Document d, Function<String, Product> resolver) {
        List<String> componentIds = d.getList("componentIds", String.class);
        if (componentIds == null || componentIds.isEmpty()) {
            return null;
        }
        List<Product> components = new ArrayList<>();
        for (String cid : componentIds) {
            Product component = resolver.apply(cid);
            if (component == null) {
                return null; // a referenced component is missing — skip the combo
            }
            components.add(component);
        }
        return new ComboProduct(
                d.getString("_id"),
                d.getString("sku"),
                d.getString("barcode"),
                d.getString("name"),
                d.getString("description"),
                d.getString("categoryId"),
                components,
                money(d.getString("fixedPrice")));
    }

    // ---- to document ----

    private static Document toDocument(Product p) {
        Document doc = new Document("_id", p.getId())
                .append("type", p.getType())
                .append("sku", p.getSku())
                .append("barcode", p.getBarcode())
                .append("name", p.getName())
                .append("description", p.getDescription())
                .append("categoryId", p.getCategoryId())
                .append("supplierId", p.getSupplierId())
                .append("unit", p.getUnit().name())
                .append("costPrice", p.getCostPrice().toPlainString())
                .append("stock", p.getStock().toPlainString())
                .append("reorderLevel", p.getReorderLevel())
                .append("batches", batchesToDocs(p));
        if (p instanceof UnitProduct u) {
            doc.append("mrp", u.getMrp().toPlainString());
        } else if (p instanceof WeighedProduct w) {
            doc.append("pricePerUnit", w.getPricePerUnit().toPlainString());
        } else if (p instanceof ComboProduct c) {
            doc.append("fixedPrice", c.getFixedPrice() == null ? null : c.getFixedPrice().toPlainString());
            doc.append("componentIds", c.getComponentIds());
        }
        return doc;
    }

    private static List<Document> batchesToDocs(Product p) {
        List<Document> docs = new ArrayList<>();
        for (Batch batch : p.getBatches()) {
            docs.add(new Document("batchNo", batch.batchNo())
                    .append("expiry", batch.expiry() == null ? null : batch.expiry().toString())
                    .append("receivedQty", batch.receivedQty().toPlainString()));
        }
        return docs;
    }

    private static ProductUnit unit(String name, ProductUnit fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return ProductUnit.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    private static BigDecimal money(String raw) {
        return raw == null || raw.isBlank() ? BigDecimal.ZERO.setScale(2) : new BigDecimal(raw);
    }

    private static BigDecimal qty(String raw) {
        return raw == null || raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
    }
}
