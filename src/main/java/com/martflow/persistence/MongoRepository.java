package com.martflow.persistence;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic Mongo adapter over the domain {@link Repository} contract: standard CRUD driven by a
 * {@link DocumentMapper}, so each new aggregate is one small mapper class instead of another
 * copy of driver plumbing (Adapter pattern).
 */
public class MongoRepository<T> implements Repository<T> {

    protected final MongoCollection<Document> collection;
    protected final DocumentMapper<T> mapper;

    protected MongoRepository(String collectionName, DocumentMapper<T> mapper) {
        this.collection = DatabaseConnection.getDatabase().getCollection(collectionName);
        this.mapper = mapper;
    }

    @Override
    public Optional<T> findById(String id) {
        Document doc = collection.find(Filters.eq("_id", id)).first();
        return doc == null ? Optional.empty() : Optional.of(mapper.fromDocument(doc));
    }

    @Override
    public List<T> findAll() {
        List<T> result = new ArrayList<>();
        for (Document doc : collection.find()) {
            result.add(mapper.fromDocument(doc));
        }
        return result;
    }

    @Override
    public T save(T entity) {
        collection.replaceOne(
                Filters.eq("_id", mapper.idOf(entity)),
                mapper.toDocument(entity),
                new ReplaceOptions().upsert(true));
        return entity;
    }

    @Override
    public void delete(String id) {
        collection.deleteOne(Filters.eq("_id", id));
    }
}
