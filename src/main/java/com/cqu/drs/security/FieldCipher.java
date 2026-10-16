package com.cqu.drs.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts the individual field values that must not sit readable
 * in the database.
 *
 * <h2>AES-256 in GCM mode, and why not CBC</h2>
 * GCM authenticates what it encrypts. A value altered directly in the database
 * fails to decrypt rather than decrypting into something else, so the mode
 * provides integrity as well as confidentiality. CBC would hand back
 * corrupted plaintext and leave the application to notice, which it would not.
 *
 * <h2>A fresh initialisation vector for every value</h2>
 * A new random 12 byte IV per encryption, stored alongside the ciphertext as
 * {@code base64(iv):base64(ciphertext)}. Reusing an IV with the same key in
 * GCM is not a small weakness: it leaks the relationship between the two
 * plaintexts and can expose the authentication key. Twelve bytes is the size
 * GCM is defined for, so no internal derivation step is needed.
 *
 * <p>The stored form carries its own IV, which is why ciphertext can be read
 * back years later without recording anything else alongside it. An IV is not
 * a secret; it only has to be unique.
 *
 * <h2>What this deliberately is not</h2>
 * It is not for passwords. Encryption is reversible by design, and a stored
 * password should not be recoverable by anybody, including the people running
 * the system. Passwords go through {@link PasswordHasher}, which is one way.
 *
 * <p>It is also not a substitute for TLS. This protects values at rest in the
 * database. A value travelling between the client and the server is protected
 * by neither this class nor the request signature, which proves integrity
 * rather than confidentiality, and that limitation is recorded in the report
 * with its remedy rather than left to be discovered.
 */
public final class FieldCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** The size GCM is specified for. */
    private static final int IV_LENGTH_BYTES = 12;

    /** The full 128 bit authentication tag. */
    private static final int TAG_LENGTH_BITS = 128;

    /** AES-256. */
    private static final int KEY_LENGTH_BYTES = 32;

    private static final String SEPARATOR = ":";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /**
     * Creates a cipher that uses the given key.
     *
     * @param keyMaterial 32 bytes of key
     * @throws IllegalArgumentException when the key is not 32 bytes, because a
     *         shorter key would quietly give AES-128 or AES-192 while the
     *         report claimed AES-256
     */
    public FieldCipher(byte[] keyMaterial) {
        if (keyMaterial == null || keyMaterial.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("An AES-256 key must be exactly "
                    + KEY_LENGTH_BYTES + " bytes, but "
                    + (keyMaterial == null ? "none" : keyMaterial.length + " bytes")
                    + " were given.");
        }
        this.key = new SecretKeySpec(keyMaterial, ALGORITHM);
    }

    /**
     * Encrypts one value.
     *
     * A null value encrypts to null rather than to the ciphertext of an empty
     * string. A column holding no contact number should hold NULL, and
     * encrypting absence would make every absent value look like the same
     * present one.
     *
     * @param plaintext the value, which may be null
     * @return the stored form, or null when there was nothing to encrypt
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext =
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder encoder = Base64.getEncoder();
            return encoder.encodeToString(iv) + SEPARATOR
                    + encoder.encodeToString(ciphertext);

        } catch (GeneralSecurityException e) {
            // Every cause here is a configuration fault rather than a bad
            // value: a key of the wrong size, or a JVM without AES. Letting
            // the write proceed would store the value readable, which is the
            // one outcome this class exists to prevent.
            throw new IllegalStateException(
                    "The value could not be encrypted, so it has not been stored. "
                            + e.getMessage(), e);
        }
    }

    /**
     * Decrypts one stored value.
     *
     * @param stored the stored form, which may be null
     * @return the original value, or null when there was nothing stored
     * @throws IllegalStateException when the stored value has been altered,
     *         when it is not in the stored form at all, or when the key is not
     *         the one it was encrypted with. The three are deliberately not
     *         distinguished to the caller, because telling an attacker which
     *         part of their guess was wrong is how a padding oracle is built
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }

        int separator = stored.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalStateException(
                    "A stored value could not be read. It is not in the encrypted form, "
                            + "which may mean it was written before encryption was "
                            + "switched on.");
        }

        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(stored.substring(0, separator));
            byte[] ciphertext = decoder.decode(stored.substring(separator + 1));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);

        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // GCM's authentication tag failing is the interesting case: it
            // means the stored bytes are not the bytes that were written. That
            // is reported as what it is, because a casualty record that has
            // been tampered with must not be displayed as though it were
            // sound.
            throw new IllegalStateException(
                    "A stored value could not be decrypted. It has been altered since "
                            + "it was written, or the encryption key has changed.", e);
        }
    }

    /**
     * Whether a value is in the stored form.
     *
     * Used when reading records written before encryption was switched on, so
     * that they can be shown rather than reported as corrupt.
     *
     * @param value the stored value
     * @return true when it looks like something this class wrote
     */
    public static boolean looksEncrypted(String value) {
        if (value == null) {
            return false;
        }
        int separator = value.indexOf(SEPARATOR);
        if (separator <= 0 || separator == value.length() - 1) {
            return false;
        }
        try {
            byte[] iv = Base64.getDecoder().decode(value.substring(0, separator));
            Base64.getDecoder().decode(value.substring(separator + 1));
            return iv.length == IV_LENGTH_BYTES;
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    /**
     * Generates a key.
     *
     * @return 32 random bytes
     */
    public static byte[] newKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        RANDOM.nextBytes(key);
        return key;
    }
}
