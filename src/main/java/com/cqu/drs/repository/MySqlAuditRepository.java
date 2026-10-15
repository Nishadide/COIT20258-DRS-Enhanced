package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.AuditLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the audit trail in MySQL, hash-chained so that alteration is
 * detectable.
 *
 * <h2>How an entry is appended</h2>
 * Appending is not a plain insert. The new entry's hash depends on the hash of
 * the entry before it, so the previous entry has to be read, and nothing else
 * may append in between. The whole thing happens in one transaction:
 *
 * <ol>
 *   <li>read the last entry {@code FOR UPDATE}, which locks it so a second
 *       thread appending at the same moment waits rather than reading the same
 *       predecessor;</li>
 *   <li>take the next sequence number and compute the new hash from it;</li>
 *   <li>insert.</li>
 * </ol>
 *
 * <p>The sequence number is assigned here rather than left to AUTO_INCREMENT,
 * because the hash covers it and so it must be known before the row is
 * written. Letting the database assign it would mean inserting a row with a
 * hash computed over a number it did not yet have, then updating the hash
 * afterwards, which leaves a window in which the stored hash is wrong.
 *
 * <p>The one case the lock cannot cover is the very first entry, when there is
 * no row to lock. Two threads can then both find the trail empty and both try
 * sequence number 1. The primary key refuses the second, and the append is
 * retried, which is correct: on the retry the first entry exists and is
 * locked normally.
 */
public final class MySqlAuditRepository implements AuditRepository {

    /** MySQL's error number for a row that breaks a unique or primary key. */
    private static final int DUPLICATE_KEY = 1062;

    /** Two transactions wanted the same rows and InnoDB rolled one back. */
    private static final int DEADLOCK = 1213;

    /** A transaction waited for a lock longer than the server allows. */
    private static final int LOCK_WAIT_TIMEOUT = 1205;

    /**
     * How many times an append is retried when it loses a race.
     *
     * Appends are serialised by a row lock, so under load they queue rather
     * than fail. Two things can still send one back to the start: two first
     * entries racing for sequence number 1 in an empty trail, and a deadlock
     * between transactions taking locks in different orders. Both are
     * ordinary, and both are resolved by trying again on a fresh transaction.
     *
     * Five is generous for contention between a handful of server threads and
     * still stops rather than spinning if something that is not contention is
     * wrong.
     */
    private static final int MAX_APPEND_ATTEMPTS = 5;

    private static final String SELECT_LAST = """
            SELECT sequence_no, entry_hash
              FROM audit_log
             ORDER BY sequence_no DESC
             LIMIT 1
               FOR UPDATE""";

    private static final String INSERT = """
            INSERT INTO audit_log
              (sequence_no, entry_id, actor, action, timestamp, previous_hash, entry_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?)""";

    private static final String SELECT_ALL = """
            SELECT sequence_no, entry_id, actor, action, timestamp,
                   previous_hash, entry_hash
              FROM audit_log
             ORDER BY sequence_no""";

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlAuditRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    @Override
    public List<AuditLogEntry> findAll() {
        List<AuditLogEntry> entries = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {

            while (results.next()) {
                entries.add(readEntry(results));
            }
            return entries;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The audit trail could not be read. " + e.getMessage(), e);
        }
    }

    @Override
    public void append(AuditLogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Cannot append a null audit entry.");
        }

        SQLException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_APPEND_ATTEMPTS; attempt++) {
            Connection connection = null;
            try {
                connection = connections.openTransaction();

                long nextSequence = 1;
                String previousHash = AuditChain.GENESIS_HASH;

                try (PreparedStatement last = connection.prepareStatement(SELECT_LAST);
                     ResultSet results = last.executeQuery()) {
                    if (results.next()) {
                        nextSequence = results.getLong("sequence_no") + 1;
                        previousHash = results.getString("entry_hash");
                    }
                }

                // The timestamp is the server's, taken when the entry was
                // created, and it is part of the hash. Nothing a client sends
                // reaches this value.
                //
                // Truncated to microseconds before it is hashed, because that
                // is the precision DATETIME(6) stores. Hashing the nanosecond
                // value and storing the microsecond one would mean the chain
                // never verified: the value read back is not the value the
                // hash was taken over. That is not a hypothetical. It is what
                // this code did until a test of an untouched trail failed.
                LocalDateTime timestamp = (entry.getTimestamp() == null
                        ? LocalDateTime.now() : entry.getTimestamp())
                        .truncatedTo(ChronoUnit.MICROS);

                // Put back on the entry so that what the caller holds matches
                // what was stored, rather than differing in digits nobody can
                // see.
                entry.setTimestamp(timestamp);

                String entryHash = AuditChain.hash(nextSequence, entry.getActor(),
                        entry.getAction(), timestamp, previousHash);

                try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                    insert.setLong(1, nextSequence);
                    insert.setString(2, entry.getEntryId());
                    insert.setString(3, entry.getActor());
                    insert.setString(4, entry.getAction());
                    insert.setObject(5, timestamp);
                    insert.setString(6, previousHash);
                    insert.setString(7, entryHash);
                    insert.executeUpdate();
                }

                connection.commit();
                return;

            } catch (SQLIntegrityConstraintViolationException clash) {
                rollback(connection);
                if (clash.getErrorCode() != DUPLICATE_KEY) {
                    throw new IllegalStateException("The audit entry was refused. "
                            + clash.getMessage(), clash);
                }
                // Another thread appended the entry this one was going to be.
                // Trying again reads its hash and chains onto it properly.
                lastFailure = clash;

            } catch (SQLException e) {
                rollback(connection);
                if (!isRetryable(e)) {
                    throw new IllegalStateException(
                            "The audit entry could not be written. " + e.getMessage(), e);
                }
                // A deadlock or a lock wait that timed out. Both are ordinary
                // outcomes of two transactions wanting the same rows, not
                // faults: InnoDB picks one to roll back so the other can
                // finish, and the one rolled back is expected to try again.
                // Treating them as failures would mean an audit entry lost
                // because two people acted at the same moment.
                lastFailure = e;

            } finally {
                close(connection);
            }
        }

        throw new IllegalStateException("The audit entry could not be appended after "
                + MAX_APPEND_ATTEMPTS + " attempts.", lastFailure);
    }

    /**
     * {@inheritDoc}
     *
     * Walks the trail from the beginning, recomputing each hash from the entry
     * before it, and stops at the first that does not match. Recomputing
     * rather than comparing stored values to each other is the point: a stored
     * previous_hash that agrees with the previous row proves only that two
     * columns agree, not that either is the hash of anything.
     */
    @Override
    public AuditChainStatus verifyChain() {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {

            String expectedPrevious = AuditChain.GENESIS_HASH;
            long expectedSequence = 1;
            int checked = 0;

            while (results.next()) {
                long sequenceNo = results.getLong("sequence_no");
                String storedPrevious = results.getString("previous_hash");
                String storedHash = results.getString("entry_hash");
                LocalDateTime timestamp =
                        results.getObject("timestamp", LocalDateTime.class);

                if (sequenceNo != expectedSequence) {
                    return AuditChainStatus.broken(checked, sequenceNo,
                            "The trail jumps from sequence " + (expectedSequence - 1)
                            + " to " + sequenceNo + ", so an entry has been removed.");
                }

                if (!expectedPrevious.equals(storedPrevious)) {
                    return AuditChainStatus.broken(checked, sequenceNo,
                            "Entry " + sequenceNo + " records a different predecessor "
                            + "than the entry that actually precedes it.");
                }

                String recomputed = AuditChain.hash(sequenceNo,
                        results.getString("actor"), results.getString("action"),
                        timestamp, storedPrevious);

                if (!recomputed.equals(storedHash)) {
                    return AuditChainStatus.broken(checked, sequenceNo,
                            "Entry " + sequenceNo + " does not hash to the value stored "
                            + "with it, so its contents have been altered since it "
                            + "was written.");
                }

                expectedPrevious = storedHash;
                expectedSequence = sequenceNo + 1;
                checked++;
            }

            return AuditChainStatus.intact(checked);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The audit trail could not be verified. " + e.getMessage(), e);
        }
    }

    /**
     * Whether a failure is one that trying again can resolve.
     *
     * A deadlock and a lock wait timeout both mean two transactions wanted the
     * same rows. Neither says anything is wrong with the entry being written,
     * and MySQL expects an application to retry rather than give up.
     *
     * @param e the failure
     * @return true when the append should be tried again
     */
    private static boolean isRetryable(SQLException e) {
        return e.getErrorCode() == DEADLOCK || e.getErrorCode() == LOCK_WAIT_TIMEOUT;
    }

    private static AuditLogEntry readEntry(ResultSet results) throws SQLException {
        // Built through the public constructor, then corrected, because the
        // constructor stamps the current time and the stored time is the one
        // that matters.
        AuditLogEntry entry = new AuditLogEntry(results.getString("entry_id"),
                results.getString("actor"), results.getString("action"));
        entry.setTimestamp(results.getObject("timestamp", LocalDateTime.class));
        return entry;
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            System.err.println("The audit transaction could not be rolled back: "
                    + e.getMessage());
        }
    }

    private static void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (SQLException e) {
            System.err.println("The connection did not close cleanly: " + e.getMessage());
        }
    }
}
