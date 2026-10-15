package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Department;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores the responding departments in MySQL.
 *
 * The departments are reference data: eight organisations the case study
 * names, fixed at seeding and changed only by an administrator marking one
 * unavailable. They are seeded here rather than by the schema because that is
 * where DRS-Initial seeded them, and keeping the arrangement means the
 * inherited tests still describe what happens.
 */
public final class MySqlDepartmentRepository implements DepartmentRepository {

    private static final String SELECT_ALL = """
            SELECT department_id, name, contact_email, available
              FROM departments
             ORDER BY department_id""";

    private static final String SELECT_AVAILABLE = """
            SELECT department_id, name, contact_email, available
              FROM departments
             WHERE available = TRUE
             ORDER BY department_id""";

    private static final String SELECT_BY_ID = """
            SELECT department_id, name, contact_email, available
              FROM departments
             WHERE department_id = ?""";

    private static final String COUNT = "SELECT COUNT(*) FROM departments";

    private static final String UPSERT = """
            INSERT INTO departments (department_id, name, contact_email, available)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name),
                                    contact_email = VALUES(contact_email),
                                    available = VALUES(available)""";

    /**
     * The eight departments, exactly as DRS-Initial seeded them.
     *
     * Kept identical so that data migrated from Assessment 2 refers to
     * departments that exist, with the identifiers its response tasks already
     * use.
     */
    private static final String[][] SEED = {
        {"DEP-01", "Fire and Emergency", "dispatch@fire.gov.au"},
        {"DEP-02", "Hospital", "emergency@health.gov.au"},
        {"DEP-03", "Electricity", "faults@power.gov.au"},
        {"DEP-04", "Transportation", "control@transport.gov.au"},
        {"DEP-05", "Waste Management", "cleanup@waste.gov.au"},
        {"DEP-06", "Water Supply", "incidents@water.gov.au"},
        {"DEP-07", "Schools", "safety@education.gov.au"},
        {"DEP-08", "Law Enforcement", "operations@police.gov.au"}
    };

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlDepartmentRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    /**
     * {@inheritDoc}
     *
     * The count and the inserts happen in one transaction, so two servers
     * started at the same moment cannot both find the table empty and both
     * seed it. The upsert makes a second attempt harmless even if one did.
     */
    @Override
    public boolean seedIfEmpty() {
        Connection connection = null;
        try {
            connection = connections.openTransaction();

            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery(COUNT)) {
                if (results.next() && results.getInt(1) > 0) {
                    connection.rollback();
                    return false;
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (String[] row : SEED) {
                    statement.setString(1, row[0]);
                    statement.setString(2, row[1]);
                    statement.setString(3, row[2]);
                    statement.setBoolean(4, true);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            connection.commit();
            return true;

        } catch (SQLException e) {
            rollback(connection);
            throw new IllegalStateException(
                    "The departments could not be seeded. " + e.getMessage(), e);
        } finally {
            close(connection);
        }
    }

    @Override
    public List<Department> findAll() {
        return query(SELECT_ALL, null);
    }

    @Override
    public List<Department> findAvailable() {
        return query(SELECT_AVAILABLE, null);
    }

    @Override
    public Optional<Department> findById(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return Optional.empty();
        }
        List<Department> found = query(SELECT_BY_ID, departmentId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public void save(Department department) {
        if (department == null) {
            throw new IllegalArgumentException("Cannot save a null department.");
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {

            statement.setString(1, department.getDepartmentId());
            statement.setString(2, department.getName());
            statement.setString(3, department.getContactEmail());
            statement.setBoolean(4, department.isAvailable());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("The department "
                    + department.getDepartmentId() + " could not be stored. "
                    + e.getMessage(), e);
        }
    }

    private List<Department> query(String sql, String parameter) {
        List<Department> departments = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Department department = new Department(
                            results.getString("department_id"),
                            results.getString("name"),
                            results.getString("contact_email"));
                    // The constructor marks a new department available, which
                    // is right for a new one and wrong for one being read back.
                    department.setAvailable(results.getBoolean("available"));
                    departments.add(department);
                }
            }
            return departments;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The departments could not be read. " + e.getMessage(), e);
        }
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            System.err.println("The transaction could not be rolled back: " + e.getMessage());
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
