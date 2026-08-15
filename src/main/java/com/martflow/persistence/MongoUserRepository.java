package com.martflow.persistence;

import com.martflow.auth.User;
import com.martflow.auth.UserRepository;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.Optional;

/** Mongo-backed user storage with username lookup for the login path. */
public class MongoUserRepository extends MongoRepository<User> implements UserRepository {

    public MongoUserRepository() {
        super("users", new UserMapper());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        Document doc = collection.find(Filters.eq("username", username)).first();
        return doc == null ? Optional.empty() : Optional.of(mapper.fromDocument(doc));
    }
}
