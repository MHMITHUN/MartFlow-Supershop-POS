package com.martflow.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PBKDF2 password hashing: round trip, constant-time verify, unique salts, strength floor. */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void roundTripVerifiesTheRightPasswordOnly() {
        String stored = hasher.hash("s3cretPass");
        assertTrue(hasher.verify("s3cretPass", stored));
        assertFalse(hasher.verify("s3cretPas", stored));
        assertFalse(hasher.verify("", stored));
        assertFalse(hasher.verify(null, stored));
    }

    @Test
    void everyHashGetsAFreshSalt() {
        String a = hasher.hash("same-password");
        String b = hasher.hash("same-password");
        assertNotEquals(a, b); // different salts -> different stored values
        assertTrue(hasher.verify("same-password", a));
        assertTrue(hasher.verify("same-password", b));
    }

    @Test
    void garbageStoredValuesFailClosed() {
        assertFalse(hasher.verify("x", null));
        assertFalse(hasher.verify("x", ""));
        assertFalse(hasher.verify("x", "not-a-hash"));
        assertFalse(hasher.verify("x", "AAAA:BBBB")); // invalid base64
    }

    @Test
    void shortPasswordsAreRejectedAtHashTime() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("12345"));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }
}
