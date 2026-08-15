package com.martflow.persistence;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the single shared {@link MongoClient} for the whole application lifecycle.
 *
 * <p><b>Pattern: Singleton.</b> A {@code MongoClient} is thread-safe and expensive to create, so
 * exactly one is shared everywhere. Implemented with a synchronized {@link #initialize()} guarded
 * by a volatile field. This is plain POJO — no Spring.
 */
public final class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);

    private static volatile MongoClient client;
    private static volatile boolean available = false;
    private static volatile java.time.LocalDateTime connectedAt;

    private DatabaseConnection() {
    }

    /**
     * Creates the shared client from {@code MONGODB_URI} and pings the database to confirm it is
     * reachable. Returns {@code true} when MongoDB Atlas is available; {@code false} (and leaves
     * the connection unset) when no URI is configured or the cluster cannot be reached, so the app
     * can fall back to the in-memory repository.
     */
    public static synchronized boolean initialize() {
        if (client != null) {
            return available;
        }
        String uri = Config.getMongoUri();
        if (uri == null || uri.isEmpty()) {
            log.info("No MONGODB_URI configured — running with in-memory storage.");
            available = false;
            return false;
        }
        try {
            MongoClient created = MongoClients.create(uri);
            String dbName = Config.getMongoDb();
            created.getDatabase(dbName).runCommand(new Document("ping", 1));
            client = created;
            available = true;
            connectedAt = com.martflow.common.TimeSource.now();
            log.info("Connected to MongoDB Atlas database '{}'. Data will persist.", dbName);
        } catch (Exception e) {
            log.warn("Could not connect to MongoDB Atlas ({}). Falling back to in-memory storage. "
                    + "Check MONGODB_URI, your database password, and the Atlas Network Access (IP) list.", e.getMessage());
            client = null;
            available = false;
        }
        return available;
    }

    public static boolean isAvailable() {
        return available;
    }

    /** When the live connection was established — null while running in-memory. */
    public static java.time.LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public static MongoDatabase getDatabase() {
        if (client == null) {
            throw new IllegalStateException("DatabaseConnection not initialized or unavailable.");
        }
        return client.getDatabase(Config.getMongoDb());
    }

    public static synchronized void close() {
        if (client != null) {
            client.close();
        }
        client = null;
        available = false;
        connectedAt = null;
    }
}
