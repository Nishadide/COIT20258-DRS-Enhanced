package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.db.SchemaInitialiser;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared setup for the tests that need a real database.
 *
 * Four repository test classes need the same three things: point at the test
 * database rather than the application's, skip cleanly when no database is
 * reachable, and start from empty tables. Writing that four times would mean
 * four chances to get it subtly different, and the one that mattered most,
 * not touching the application's data, is the one that would hurt to get
 * wrong.
 */
public final class DatabaseTestSupport {

    /**
     * The database the tests work in.
     *
     * Never the one the application uses. These tests empty the tables they
     * touch, and once the Assessment 2 data has been migrated, running them
     * against the real database would destroy it.
     */
    public static final String TEST_DATABASE = "drs_enhanced_test";

    /** Child tables first, so the order works whether or not deletes cascade. */
    private static final String[] ALL_TABLES = {
        "status_updates", "alerts", "response_tasks", "assessments",
        "disaster_reports", "reporters", "request_log", "audit_log",
        "users", "departments"
    };

    /** Not instantiable: this class holds static helpers only. */
    private DatabaseTestSupport() {
    }

    /**
     * Points configuration at the test database.
     *
     * @return whatever the setting held before, to be given back to
     *         {@link #restoreDatabaseName}
     */
    public static String useTestDatabase() {
        String original = System.getProperty(DatabaseConfig.KEY_DB_NAME);
        System.setProperty(DatabaseConfig.KEY_DB_NAME, TEST_DATABASE);
        return original;
    }

    /**
     * Puts the setting back.
     *
     * Always called from an {@code @AfterEach}, including when the test was
     * skipped, because leaving it pointing at the test database would send
     * every later test in the same JVM somewhere unexpected.
     *
     * @param original the value returned by {@link #useTestDatabase}
     */
    public static void restoreDatabaseName(String original) {
        if (original == null) {
            System.clearProperty(DatabaseConfig.KEY_DB_NAME);
        } else {
            System.setProperty(DatabaseConfig.KEY_DB_NAME, original);
        }
    }

    /**
     * Connects to the test database and makes sure its schema exists, or skips
     * the test when no database is reachable.
     *
     * @return a factory for the test database
     * @throws SQLException when the schema cannot be created
     */
    public static ConnectionFactory connectOrSkip() throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        ConnectionFactory connections = new ConnectionFactory(config);

        Assumptions.assumeTrue(isReachable(connections),
                "No MySQL database is reachable at " + config.describeTarget()
                        + ", so the database tests are skipped. Start MySQL and set "
                        + "DRS_DB_PASSWORD to run them.");

        new SchemaInitialiser(connections).initialise();
        return connections;
    }

    private static boolean isReachable(ConnectionFactory connections) {
        try {
            connections.readServerVersion();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Empties every table, so one test cannot be affected by what another left
     * behind.
     *
     * @param connections the test database
     * @throws SQLException when a delete fails
     */
    public static void clearAll(ConnectionFactory connections) throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            for (String table : ALL_TABLES) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
    }
}
