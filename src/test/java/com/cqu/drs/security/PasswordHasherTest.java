package com.cqu.drs.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T341 to T347: password storage.
 *
 * The change from Assessment 2 is the point of these. DRS-Initial stored an
 * unsalted SHA-256 digest, so two users who chose the same password stored
 * identical values and a stolen table fell to a dictionary attack quickly.
 * These check that the replacement actually has the properties that fixes:
 * same password, different stored value; and the old format still accepted so
 * that migrated accounts can sign in once and be upgraded.
 */
@DisplayName("Password storage")
class PasswordHasherTest {

    @Test
    @DisplayName("T341: a password verifies against its own hash")
    void passwordVerifies() {
        String stored = PasswordHasher.hash("correct horse battery");

        assertTrue(PasswordHasher.matches("correct horse battery", stored));
        assertFalse(PasswordHasher.matches("Correct horse battery", stored));
        assertFalse(PasswordHasher.matches("", stored));
        assertFalse(PasswordHasher.matches(null, stored));
    }

    @Test
    @DisplayName("T342: the stored form does not contain the password")
    void storedFormHidesThePassword() {
        String stored = PasswordHasher.hash("admin123");
        assertFalse(stored.contains("admin123"), stored);
    }

    @Test
    @DisplayName("T343: two users with the same password store different values")
    void samePasswordStoresDifferently() {
        // The salt. This is the property Assessment 2 lacked, and the reason a
        // stolen table could be attacked in bulk rather than one account at a
        // time.
        Set<String> stored = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            stored.add(PasswordHasher.hash("admin123"));
        }
        assertEquals(10, stored.size(),
                "Ten hashes of one password produced " + stored.size()
                        + " distinct values. Each must have its own salt.");

        // And every one of them still verifies.
        for (String hash : stored) {
            assertTrue(PasswordHasher.matches("admin123", hash));
        }
    }

    @Test
    @DisplayName("T344: an Assessment 2 digest is still accepted")
    void legacyDigestStillVerifies() {
        // Migrated accounts hold these. Refusing them would lock out every
        // user carried over from Assessment 2.
        String legacy = PasswordHasher.legacySha256("admin123");

        assertTrue(PasswordHasher.matches("admin123", legacy));
        assertFalse(PasswordHasher.matches("wrong", legacy));
    }

    @Test
    @DisplayName("T345: an Assessment 2 digest is reported as needing upgrade")
    void legacyDigestNeedsUpgrade() {
        assertTrue(PasswordHasher.needsUpgrade(PasswordHasher.legacySha256("admin123")));
        assertFalse(PasswordHasher.needsUpgrade(PasswordHasher.hash("admin123")));
        assertFalse(PasswordHasher.needsUpgrade(null));
        assertFalse(PasswordHasher.needsUpgrade("  "));
    }

    @Test
    @DisplayName("T346: the new format is distinguishable from the old one")
    void formatsAreDistinguishable() {
        String modern = PasswordHasher.hash("admin123");
        String legacy = PasswordHasher.legacySha256("admin123");

        assertTrue(modern.startsWith("pbkdf2$"), modern);
        assertFalse(legacy.startsWith("pbkdf2$"), legacy);
        assertNotEquals(modern, legacy);

        // The iteration count travels with the hash, so the cost can be
        // raised later without old hashes becoming unverifiable.
        assertTrue(modern.contains("$210000$"), modern);
    }

    @Test
    @DisplayName("T347: a malformed stored value is refused rather than crashing")
    void malformedStoredValueIsRefused() {
        assertFalse(PasswordHasher.matches("admin123", "pbkdf2$nonsense"));
        assertFalse(PasswordHasher.matches("admin123", "pbkdf2$abc$def$ghi"));
        assertFalse(PasswordHasher.matches("admin123", ""));
    }
}
