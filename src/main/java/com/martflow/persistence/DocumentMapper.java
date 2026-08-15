package com.martflow.persistence;

import org.bson.Document;

/**
 * Maps an aggregate to and from a Mongo document. The generic {@link MongoRepository} drives the
 * CRUD; each aggregate only supplies this mapping (that specialisation is the Adapter's varying
 * part).
 */
public interface DocumentMapper<T> {

    Document toDocument(T entity);

    T fromDocument(Document document);

    /** The entity's id — used as the Mongo {@code _id}. */
    String idOf(T entity);
}
