package com.cqu.drs.migration;

import com.cqu.drs.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The migration ledger, kept in the schema_metadata table.
 *
 * That table was created in commit 6 for exactly this: recording what has been
 * applied to the database, as distinct from what the database holds. It is the
 * one table whose rows describe the schema's own history rather than the
 * disaster response.
 */
public final class MySqlMigrationLedger implements MigrationLedger {

    private static final String SELECT = """
            SELECT detail, applied_at
              FROM schema_metadata
             WHERE item = ?""";

    /**
     * Written with ON DUPLICATE KEY UPDATE so that recording twice is not an
     * error. It should not happen, because the caller asks {@link #hasRun}
     * first, but between that question and this answer another server thread
     * could have recorded it, and losing a migration to a race would be worse
     * than recording the later of two identical facts.
     */
    private static final String UPSERT = """
            INSERT INTO schema_metadata (item, applied_at, detail)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE applied_at = VALUES(applied_at),
                                    detail = VALUES(detail)""";

    private final ConnectionFactory connections;

    /**
     * Creates the ledger.
     *
     * @param connections where connections come from
     */
    public MySqlMigrationLedger(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A ledger needs a connection factory.");
        }
        this.connections = connections;
    }

    @Override
    public boolean hasRun(String item) {
        return detailOf(item).isPresent();
    }

    @Override
    public Optional<String> detailOf(String item) {
        if (item == null || item.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {

            statement.setString(1, item);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                String detail = results.getString("detail");
                LocalDateTime appliedAt =
                        results.getObject("applied_at", LocalDateTime.class);

                // The time is part of the answer. "Already done" is far less
                // use to somebody investigating than "already done, on this
                // date, and this is what it moved".
                return Optional.of((detail == null ? "No detail was recorded." : detail)
                        + " Applied at " + appliedAt + ".");
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The migration ledger could not be read. " + e.getMessage(), e);
        }
    }

    @Override
    public void record(String item, String detail) {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("A ledger entry needs a name.");
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {

            statement.setString(1, item);
            // Truncated to microseconds because that is what DATETIME(6)
            // stores. Writing nanoseconds and reading microseconds back is
            // how the audit chain came to disagree with itself, and the same
            // mistake is not worth making twice.
            statement.setObject(2, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            statement.setString(3, detail);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The migration could not be recorded, so it has not been "
                            + "treated as done. " + e.getMessage(), e);
        }
    }
}
