package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Stores the request log in MySQL.
 *
 * Every statement here uses parameter markers. No value is ever concatenated
 * into SQL, so a username, an operation name or a signature cannot be read as
 * SQL however it is spelled. The one place in the whole application where
 * concatenation is unavoidable is the database name in CREATE DATABASE, which
 * is validated separately and explained in SchemaInitialiser.
 */
public final class MySqlRequestLogRepository implements RequestLogRepository {

    private static final String INSERT = """
            INSERT INTO request_log
              (request_id, session_token, actor, operation, client_address,
               sent_at, received_at, status, signature)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ERROR', ?)""";

    private static final String COMPLETE = """
            UPDATE request_log
               SET status = ?, error_code = ?, responded_at = ?
             WHERE request_id = ?""";

    private static final String COUNT = "SELECT COUNT(*) FROM request_log";

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlRequestLogRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    /**
     * {@inheritDoc}
     *
     * The row is inserted with a status of ERROR and no responded_at, and
     * {@link #complete} overwrites both once the request has been answered.
     *
     * Inserting a provisional ERROR rather than leaving the status empty is
     * deliberate. The status column permits only OK or ERROR, and a row that
     * records a request received but never answered is accurately described as
     * an error: it is precisely what a server killed part way through a
     * request leaves behind. A provisional value of OK would have meant the
     * opposite, and a crash would have left the log claiming success.
     */
    @Override
    public boolean claim(RequestLogEntry entry) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {

            statement.setString(1, entry.requestId());
            statement.setString(2, entry.sessionToken());
            statement.setString(3, entry.actor());
            statement.setString(4, entry.operation());
            statement.setString(5, entry.clientAddress());
            setTimestamp(statement, 6, entry.sentAt());
            setTimestamp(statement, 7, entry.receivedAt());
            statement.setString(8, entry.signature());

            statement.executeUpdate();
            return true;

        } catch (SQLIntegrityConstraintViolationException duplicate) {
            // The unique key on request_id rejected this identifier, so it has
            // been handled before. This is the replay check, and it is a
            // normal outcome rather than a fault, so nothing is logged and no
            // exception escapes.
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The request log could not be written, so the request was "
                            + "not processed. " + e.getMessage(), e);
        }
    }

    @Override
    public void complete(String requestId, String status, String errorCode,
                         LocalDateTime respondedAt) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(COMPLETE)) {

            statement.setString(1, status);
            statement.setString(2, errorCode);
            setTimestamp(statement, 3, respondedAt);
            statement.setString(4, requestId);

            statement.executeUpdate();

        } catch (SQLException e) {
            // Failing to complete the row must not turn a successful request
            // into a failed one for the client, who has already been served.
            // The gap is visible in the log as a row with no responded_at.
            System.err.println("Warning: the request log could not be completed for "
                    + requestId + ". " + e.getMessage());
        }
    }

    @Override
    public int count() {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(COUNT)) {
            return results.next() ? results.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The request log could not be counted. " + e.getMessage(), e);
        }
    }

    /**
     * Writes a LocalDateTime, or a SQL NULL when it is absent.
     *
     * setObject with a LocalDateTime maps straight onto the MySQL DATETIME
     * type with no time zone conversion in either direction, which is the
     * whole reason the columns are DATETIME rather than TIMESTAMP.
     *
     * @param statement the statement being filled in
     * @param index     the parameter position
     * @param value     the time, which may be null
     * @throws SQLException when the parameter cannot be set
     */
    private static void setTimestamp(PreparedStatement statement, int index,
                                     LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, value);
        }
    }
}
