package com.cqu.drs.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T331 to T340: the field encryption required by section 2.5.
 *
 * <h2>What each test is actually checking</h2>
 * A round trip proves almost nothing on its own: a cipher that returned its
 * input unchanged would pass it. So the round trip is one test among several,
 * and the others check the properties that make the encryption worth having.
 * That the ciphertext does not contain the plaintext. That encrypting the same
 * value twice gives different output, so an attacker holding the table cannot
 * tell which casualties share a contact number. That a ciphertext altered by
 * one character fails to decrypt rather than returning something plausible.
 */
@DisplayName("Field encryption")
class FieldCipherTest {

    private final FieldCipher cipher = new FieldCipher(FieldCipher.newKey());

    @Test
    @DisplayName("T331: a value comes back exactly as it went in")
    void roundTripIsExact() {
        String name = "Priya Raman";
        assertEquals(name, cipher.decrypt(cipher.encrypt(name)));

        String notes = "Compound fracture, left leg. Morphine 5mg at 14:20.";
        assertEquals(notes, cipher.decrypt(cipher.encrypt(notes)));
    }

    @Test
    @DisplayName("T332: the stored form does not contain the value")
    void storedFormHidesTheValue() {
        String contact = "0412345678";
        String stored = cipher.encrypt(contact);

        assertFalse(stored.contains(contact),
                "The point of the exercise is that this value is not in the "
                        + "database: " + stored);
        assertNotEquals(contact, stored);
    }

    @Test
    @DisplayName("T333: the same value encrypts differently every time")
    void sameValueEncryptsDifferently() {
        // A fresh initialisation vector per value. Without this, two
        // casualties with the same contact number would store identical
        // ciphertext, and anybody holding the table would know they were the
        // same number without being able to read it.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            seen.add(cipher.encrypt("0412345678"));
        }
        assertEquals(20, seen.size(),
                "Twenty encryptions of one value produced " + seen.size()
                        + " distinct results. They must all differ.");
    }

    @Test
    @DisplayName("T334: a ciphertext altered by one character will not decrypt")
    void tamperingIsDetected() {
        String stored = cipher.encrypt("Priya Raman");

        // Flip one character of the ciphertext, as somebody editing the
        // database directly would. GCM authenticates what it encrypts, so
        // this fails rather than returning different plaintext.
        char[] characters = stored.toCharArray();
        int last = characters.length - 1;
        characters[last] = characters[last] == 'A' ? 'B' : 'A';
        String altered = new String(characters);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(altered));
    }

    @Test
    @DisplayName("T335: a different key cannot read what this one wrote")
    void anotherKeyCannotRead() {
        String stored = cipher.encrypt("Priya Raman");
        FieldCipher other = new FieldCipher(FieldCipher.newKey());

        assertThrows(IllegalStateException.class, () -> other.decrypt(stored));
    }

    @Test
    @DisplayName("T336: absence stays absent rather than becoming ciphertext")
    void nullStaysNull() {
        // A casualty with no contact number should hold NULL. Encrypting
        // absence would make every absent value look like the same present
        // one, which is information an attacker does not need handed to them.
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
    }

    @Test
    @DisplayName("T337: an empty string is a value and is encrypted like one")
    void emptyStringIsStillEncrypted() {
        String stored = cipher.encrypt("");
        assertTrue(FieldCipher.looksEncrypted(stored));
        assertEquals("", cipher.decrypt(stored));
    }

    @Test
    @DisplayName("T338: a key of the wrong size is refused rather than silently weakening")
    void wrongSizedKeyIsRefused() {
        // 16 bytes would give AES-128 without complaint, while the report
        // claimed AES-256. Refusing is the only way the claim stays true.
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCipher(new byte[16]));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCipher(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new FieldCipher(null));
    }

    @Test
    @DisplayName("T339: plain text is recognised as not encrypted")
    void plainTextIsRecognised() {
        // Used when reading records written before encryption was switched
        // on, so they can be shown rather than reported as corrupt.
        assertFalse(FieldCipher.looksEncrypted("Priya Raman"));
        assertFalse(FieldCipher.looksEncrypted("ward 3: bed 2"));
        assertFalse(FieldCipher.looksEncrypted(""));
        assertFalse(FieldCipher.looksEncrypted(null));
        assertTrue(FieldCipher.looksEncrypted(cipher.encrypt("Priya Raman")));
    }

    @Test
    @DisplayName("T340: each generated key differs from the last")
    void keysAreNotPredictable() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            keys.add(java.util.Base64.getEncoder().encodeToString(FieldCipher.newKey()));
        }
        assertEquals(20, keys.size(), "Generated keys must not repeat.");
    }
}
