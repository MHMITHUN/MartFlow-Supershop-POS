package com.martflow.persistence;

import com.martflow.auth.User;
import com.martflow.auth.UserRepository;

import java.util.Optional;

/** In-memory user storage fallback (no Mongo configured). */
public class InMemoryUserRepository extends InMemoryRepository<User> implements UserRepository {

    public InMemoryUserRepository() {
        super(User::getId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(u -> username.equals(u.getUsername()))
                .findFirst();
    }
}
