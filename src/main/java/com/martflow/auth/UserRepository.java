package com.martflow.auth;

import java.util.Optional;

/** User storage with username lookup (login) on top of the standard repository contract. */
public interface UserRepository extends com.martflow.persistence.Repository<User> {

    Optional<User> findByUsername(String username);
}
