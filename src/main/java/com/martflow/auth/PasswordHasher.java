package com.martflow.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2WithHmacSHA256 (JDK built-in — no extra dependency, keeping the
 * zero-dependency POJO philosophy). Each user gets a random 16-byte salt; the derived key is
 * 256-bit over 120,000 iterations. Verification is constant-time to avoid timing leaks.
 *
 * <p>Stored format: {@code base64(salt):base64(derivedKey)}.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Hashes a plaintext password with a fresh random salt. */
    public String hash(String password) {
        requireDecent(password);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = derive(password.toCharArray(), salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(key);
    }

    /** Verifies a plaintext password against a stored {@code hash(password)} value. */
    public boolean verify(String password, String stored) {
        if (password == null || stored == null || !stored.contains(":")) {
            return false;
        }
        try {
            String[] parts = stored.split(":", 2);
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            byte[] actual = derive(password.toCharArray(), salt);
            return MessageDigest.isEqual(expected, actual); // constant-time
        } catch (IllegalArgumentException badEncoding) {
            return false;
        }
    }

    private byte[] derive(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Password hashing failed", e);
        }
    }

    private static void requireDecent(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }
}
