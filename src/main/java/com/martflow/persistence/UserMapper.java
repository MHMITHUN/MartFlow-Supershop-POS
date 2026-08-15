package com.martflow.persistence;

import com.martflow.auth.User;
import com.martflow.common.TimeSource;
import com.martflow.security.Role;
import org.bson.Document;

import java.time.LocalDateTime;

/** Maps {@link User} documents (dates as ISO strings, everything else scalar). */
public class UserMapper implements DocumentMapper<User> {

    @Override
    public Document toDocument(User user) {
        return new Document("_id", user.getId())
                .append("username", user.getUsername())
                .append("fullName", user.getFullName())
                .append("passwordHash", user.getPasswordHash())
                .append("role", user.getRole().name())
                .append("active", user.isActive())
                .append("createdAt", user.getCreatedAt().toString());
    }

    @Override
    public User fromDocument(Document d) {
        LocalDateTime createdAt;
        try {
            createdAt = LocalDateTime.parse(d.getString("createdAt"));
        } catch (Exception unparseable) {
            createdAt = TimeSource.now();
        }
        return new User(
                d.getString("_id"),
                d.getString("username"),
                d.getString("fullName"),
                d.getString("passwordHash"),
                Role.valueOf(d.getString("role")),
                d.getBoolean("active", true),
                createdAt);
    }

    @Override
    public String idOf(User user) {
        return user.getId();
    }
}
