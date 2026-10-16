package com.cqu.drs.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Turns a password into something that can be stored, and checks a password
 * against what was stored.
 *
 * <h2>Hashing, not encryption, and why the distinction is not pedantic</h2>
 * An encrypted password can be decrypted by whoever holds the key, which
 * includes everybody who runs the system. A hashed password cannot be
 * recovered by anyone. Nobody needs to know a user's password, including the
 * administrator, so the irreversible option is the correct one and the
 * reversible one would be a liability with no benefit.
 *
 * <h2>What Assessment 2 did and why it is not enough</h2>
 * DRS-Initial stored a plain SHA-256 digest with no salt. Two users who chose
 * the same password had identical stored values, which tells an attacker who
 * has the table that those accounts share a password without breaking either.
 * Worse, SHA-256 is built to be fast, and fast is the opposite of what is
 * wanted here: a modern graphics card computes billions of SHA-256 digests a
 * second, so a stolen table of unsalted digests falls to a dictionary attack
 * quickly.
 *
 * <p>This replaces it with PBKDF2 using HMAC-SHA256, a random 16 byte salt per
 * user, and 210,000 iterations. The salt makes identical passwords store
 * differently and makes precomputed tables useless. The iteration count makes
 * each guess cost about the same as 210,000 digests, which a legitimate
 * sign-in pays once and an attacker pays for every guess.
 *
 * <h2>Both formats are readable, and that is deliberate</h2>
 * Accounts carried over from Assessment 2 hold bare SHA-256 digests. Refusing
 * them would lock out every migrated user. Instead {@link #matches} recognises
 * which format it is looking at, and {@link #needsUpgrade} says when an old
 * one should be replaced, which the sign-in does with the password it has just
 * been given and verified. Each user is upgraded the next time they sign in,
 * without anybody being asked to do anything.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Marks the stored form, so the two generations can be told apart. */
    private static final String PREFIX = "pbkdf2$";

    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    /**
     * How much work each guess costs.
     *
     * OWASP's guidance for PBKDF2-HMAC-SHA256. High enough to make bulk
     * guessing expensive, low enough that a sign-in is not noticeably slow.
     */
    private static final int ITERATIONS = 210_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Not instantiable: this class holds static methods only. */
    private PasswordHasher() {
    }

    /**
     * Hashes a password for storage.
     *
     * @param plainPassword the password
     * @return the stored form, carrying its own salt and iteration count
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("A password is required.");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        return PREFIX + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derive(plainPassword, salt, ITERATIONS));
    }

    /**
     * Checks a password against a stored value, in either format.
     *
     * @param plainPassword the password offered
     * @param stored        what is held for that account
     * @return true when they match
     */
    public static boolean matches(String plainPassword, String stored) {
        if (plainPassword == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (!stored.startsWith(PREFIX)) {
            // An Assessment 2 digest. Checked the way it was made, so migrated
            // users can still sign in, and upgraded immediately afterwards.
            return MessageDigest.isEqual(legacySha256(plainPassword).getBytes(),
                    stored.getBytes());
        }

        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(plainPassword, salt, iterations);

            // Constant time. A comparison that stopped at the first differing
            // byte would take measurably longer the more of the hash an
            // attacker had guessed correctly, which is a way to recover it one
            // byte at a time.
            return MessageDigest.isEqual(expected, actual);

        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    /**
     * Whether a stored value is in the old format and should be replaced.
     *
     * @param stored what is held for the account
     * @return true when it is an Assessment 2 digest
     */
    public static boolean needsUpgrade(String stored) {
        return stored != null && !stored.isBlank() && !stored.startsWith(PREFIX);
    }

    /**
     * The Assessment 2 hashing, kept only so that migrated accounts can be
     * verified once and then upgraded.
     *
     * @param plainPassword the password
     * @return the base64 SHA-256 digest DRS-Initial stored
     */
    public static String legacySha256(String plainPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(plainPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static byte[] derive(String plainPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(plainPassword.toCharArray(), salt,
                    iterations, KEY_LENGTH_BITS);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec)
                        .getEncoded();
            } finally {
                // Clears the copy of the password this object holds. The
                // String itself cannot be cleared, which is a known limitation
                // of handling passwords as Strings at all.
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "The password could not be hashed. " + e.getMessage(), e);
        }
    }
}
