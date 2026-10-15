package com.cqu.drs.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Computes the hashes that chain the audit trail together.
 *
 * Each entry's hash is taken over its own contents and the hash of the entry
 * before it:
 *
 * <pre>
 *   entryHash = SHA-256( sequenceNo | actor | action | timestamp | previousHash )
 * </pre>
 *
 * <p>Because every hash depends on the one before, it depends transitively on
 * every entry that precedes it. Altering an entry, removing one, or inserting
 * one in the middle changes that entry's hash and breaks every hash after it,
 * so a trail that still verifies is a trail that has not been touched.
 *
 * <p>Kept in its own class, with no database and no model in it, so the rule
 * is written once and the same code both writes a hash and checks it. Two
 * separate implementations of the same formula is how a chain quietly stops
 * verifying.
 *
 * <h2>What this does and does not protect against</h2>
 * It detects alteration by anyone who cannot recompute the whole chain. It
 * does not stop someone with full access to the database from altering an
 * entry and then recomputing every hash after it. That limitation is inherent
 * to keeping the chain in the same place as the data, and the honest remedies,
 * an append-only store or publishing the head hash somewhere separate, are out
 * of scope for this assessment. The report states this rather than implying a
 * protection that is not there.
 */
public final class AuditChain {

    /** The previousHash of the very first entry: sixty-four zeros. */
    public static final String GENESIS_HASH = "0".repeat(64);

    /** Field separator. A character that cannot appear in a SHA-256 hash. */
    private static final String SEPARATOR = "|";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Not instantiable: this class holds one calculation. */
    private AuditChain() {
    }

    /**
     * The hash for one entry.
     *
     * @param sequenceNo   the entry's position in the trail
     * @param actor        who performed the action
     * @param action       what was done
     * @param timestamp    when, by the server's clock
     * @param previousHash the hash of the entry before, or {@link #GENESIS_HASH}
     * @return the hash, as sixty-four lowercase hexadecimal characters
     */
    public static String hash(long sequenceNo, String actor, String action,
                              LocalDateTime timestamp, String previousHash) {
        String canonical = sequenceNo
                + SEPARATOR + nullSafe(actor)
                + SEPARATOR + nullSafe(action)
                + SEPARATOR + (timestamp == null ? "" : TIMESTAMP.format(timestamp))
                + SEPARATOR + nullSafe(previousHash);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java implementation.
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
