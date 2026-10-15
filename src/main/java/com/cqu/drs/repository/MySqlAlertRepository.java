package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores public alerts in MySQL.
 *
 * <h2>Why this does not write the report reference</h2>
 * An Alert carries no reference to the disaster it was issued for. The report
 * owns its alerts, so {@code MySqlDisasterReportRepository} writes that
 * column, and {@code AlertService} stores each alert twice: once through the
 * report and once through here.
 *
 * <p>That means this class must leave {@code reference_number} alone when it
 * updates a row, which is why it is absent from the update clause below.
 * Including it would set the column to null on every save, and the alert would
 * silently detach from the disaster it belongs to while still appearing in the
 * list of all alerts. The two writers can run in either order and the result
 * is the same, because each writes only the columns it actually knows about.
 */
public final class MySqlAlertRepository implements AlertRepository {

    private static final String SELECT_ALL = """
            SELECT alert_id, reference_number, type, affected_area, message,
                   issued_at, delivered, issued_by
              FROM alerts
             ORDER BY issued_at""";

    /**
     * reference_number is written on insert, as null, and deliberately left
     * out of the update clause. See the note on this class.
     */
    private static final String UPSERT = """
            INSERT INTO alerts
              (alert_id, reference_number, type, affected_area, message,
               issued_at, delivered, issued_by)
            VALUES (?, NULL, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE type = VALUES(type),
                                    affected_area = VALUES(affected_area),
                                    message = VALUES(message),
                                    issued_at = VALUES(issued_at),
                                    delivered = VALUES(delivered),
                                    issued_by = VALUES(issued_by)""";

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlAlertRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    @Override
    public List<Alert> findAll() {
        List<Alert> alerts = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {

            while (results.next()) {
                Alert alert = new Alert(
                        results.getString("alert_id"),
                        AlertType.valueOf(results.getString("type")),
                        results.getString("affected_area"),
                        results.getString("message"),
                        results.getString("issued_by"));
                // The constructor stamps the current time and marks the alert
                // undelivered, which is right for a new alert and wrong for
                // one being read back.
                alert.setIssuedAt(results.getObject("issued_at", LocalDateTime.class));
                alert.setDelivered(results.getBoolean("delivered"));
                alerts.add(alert);
            }
            return alerts;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The alerts could not be read. " + e.getMessage(), e);
        }
    }

    @Override
    public void save(Alert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("Cannot save a null alert.");
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {

            statement.setString(1, alert.getAlertId());
            statement.setString(2, alert.getType().name());
            statement.setString(3, alert.getAffectedArea());
            statement.setString(4, alert.getMessage());
            statement.setObject(5, alert.getIssuedAt());
            statement.setBoolean(6, alert.isDelivered());
            statement.setString(7, alert.getIssuedBy());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("The alert " + alert.getAlertId()
                    + " could not be stored. " + e.getMessage(), e);
        }
    }
}
