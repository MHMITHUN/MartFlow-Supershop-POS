package com.martflow.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads configuration from the process environment, falling back to a local {@code .env} file.
 *
 * <p>The MongoDB Atlas connection string MUST be supplied this way (via {@code MONGODB_URI}).
 * It is never hardcoded anywhere in the source. See design rule D3.
 *
 * <p>This is a plain utility class (no Spring) so the persistence layer stays framework-free.
 */
public final class Config {

    private static final String ENV_FILE = ".env";
    private static final Map<String, String> DOTENV = loadDotenv();

    private Config() {
    }

    private static Map<String, String> loadDotenv() {
        Map<String, String> map = new HashMap<>();
        Path path = Paths.get(ENV_FILE);
        if (!Files.exists(path)) {
            return map;
        }
        try {
            for (String raw : Files.readAllLines(path)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                map.put(key, val);
            }
        } catch (IOException ignored) {
            // Real env vars may still be set; carry on.
        }
        return map;
    }

    /** Returns the value of {@code key}: process env first, then {@code .env} file. */
    public static String get(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        return DOTENV.get(key);
    }

    public static String get(String key, String defaultValue) {
        String v = get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }

    public static String getRequired(String key) {
        String v = get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException("Missing required configuration: " + key);
        }
        return v;
    }

    public static String getMongoUri() {
        return get("MONGODB_URI");
    }

    public static String getMongoDb() {
        return get("MONGODB_DB", "martflow");
    }

    public static boolean hasMongoUri() {
        String uri = getMongoUri();
        return uri != null && !uri.isEmpty();
    }
}
