package com.cqu.drs.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens connections to the MySQL database.
 *
 * Every data access class takes a ConnectionFactory rather than a Connection,
 * and opens its own connection inside a try-with-resources block. This is the
 * reason:
 *
 * <p>The server handles each client on its own thread. A JDBC Connection is
 * not safe to share between threads, because a transaction belongs to a
 * connection, so two threads sharing one connection would share one
 * transaction and either could commit the other's half-finished work. Giving
 * each operation its own connection means each transaction belongs to exactly
 * one request on one thread.
 *
 * <p>A connection is opened per operation rather than pooled. For an
 * assessment prototype on a local MySQL instance the cost is a few
 * milliseconds and the correctness is easier to argue. A production system
 * would place a pool behind this same class, which is one of the reasons the
 * rest of the code depends on this class and not on DriverManager.
 *
 * <p>No class in this package imports com.mysql.cj. Everything is written
 * against the java.sql interfaces in the JDK, so the project compiles without
 * the driver present and needs it only at run time.
 */
public final class ConnectionFactory {

    private final DatabaseConfig config;

    /**
     * Creates a factory for the given configuration.
     *
     * @param config where the host, port, database, user and password come
     *               from
     */
    public ConnectionFactory(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("A connection factory needs a configuration.");
        }
        this.config = config;
    }

    /**
     * Creates a factory using the configuration found on the classpath and in
     * the environment.
     */
    public ConnectionFactory() {
        this(DatabaseConfig.load());
    }

    /**
     * The configuration this factory connects with.
     *
     * @return the configuration
     */
    public DatabaseConfig getConfig() {
        return config;
    }

    /**
     * Opens a connection to the DRS database.
     *
     * The caller is responsible for closing it, which in practice means
     * opening it in a try-with-resources block.
     *
     * @return an open connection with auto-commit left at its default of true
     * @throws SQLException when the database cannot be reached, or the driver
     *                      is absent from the classpath
     */
    public Connection open() throws SQLException {
        return connect(config.jdbcUrl());
    }

    /**
     * Opens a connection to the MySQL server without naming a database.
     *
     * Used only by the schema initialiser, which has to issue CREATE DATABASE
     * before the database it would otherwise connect to exists.
     *
     * @return an open connection
     * @throws SQLException when the server cannot be reached
     */
    public Connection openWithoutDatabase() throws SQLException {
        return connect(config.jdbcUrlWithoutDatabase());
    }

    /**
     * Opens a connection with auto-commit switched off, for an operation that
     * must either complete in full or leave no trace.
     *
     * Dispatching a disaster to several departments is one such operation: it
     * writes one row per department and updates the report's status, and a
     * failure part way through must not leave a report marked as dispatched
     * with only some of its tasks present.
     *
     * @return an open connection in manual commit mode
     * @throws SQLException when the database cannot be reached
     */
    public Connection openTransaction() throws SQLException {
        Connection connection = open();
        try {
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            // Closing here prevents a connection leaking when only the
            // auto-commit change failed.
            try {
                connection.close();
            } catch (SQLException ignored) {
                // The original failure is the one worth reporting.
            }
            throw e;
        }
    }

    private Connection connect(String url) throws SQLException {
        try {
            return DriverManager.getConnection(
                    url, config.databaseUser(), config.databasePassword());
        } catch (SQLException e) {
            throw new SQLException(explain(e), e.getSQLState(), e.getErrorCode(), e);
        }
    }

    /**
     * Turns a JDBC failure into a message that names the likely cause.
     *
     * The messages JDBC produces for the three mistakes made most often when
     * setting this project up, which are a missing driver, a stopped MySQL
     * service and a wrong password, do not say what to do about them. This
     * method adds that, because an error a marker cannot act on costs marks
     * that the code itself has already earned.
     *
     * @param cause the original failure
     * @return a message naming the probable cause and the remedy
     */
    private String explain(SQLException cause) {
        String original = cause.getMessage() == null ? "" : cause.getMessage();
        String state = cause.getSQLState() == null ? "" : cause.getSQLState();
        String target = config.describeTarget();

        if (original.contains("No suitable driver")) {
            return "The MySQL JDBC driver is not on the classpath, so no connection to "
                    + target + " could be made. Run \"mvn clean compile\" to download "
                    + "mysql-connector-j, or check that the dependency is present in "
                    + "pom.xml. Original message: " + original;
        }
        // 08S01 is a communications link failure, which is what a stopped
        // MySQL service produces.
        if (state.startsWith("08") || original.contains("Communications link failure")
                || original.contains("Connection refused")) {
            return "MySQL could not be reached at " + config.databaseHost() + ":"
                    + config.databasePort() + ". Check that the MySQL service is "
                    + "running, then that the host and port in db.properties are "
                    + "correct. Original message: " + original;
        }
        // 28000 is an invalid authorisation specification.
        if (state.equals("28000") || original.contains("Access denied for user")) {
            return "MySQL refused the credentials for " + target
                    + ". Set the correct password with -Ddrs.db.password=yourpassword "
                    + "or correct drs.db.user and drs.db.password in db.properties. "
                    + "Original message: " + original;
        }
        if (original.contains("Public Key Retrieval is not allowed")) {
            return "MySQL 8 refused the connection to " + target
                    + " because allowPublicKeyRetrieval is not set. Restore the "
                    + "drs.db.params line in db.properties, which includes "
                    + "allowPublicKeyRetrieval=true. Original message: " + original;
        }
        // 42000 with this text is an unknown database, which is expected
        // before the schema has been created.
        if (original.contains("Unknown database")) {
            return "The database " + config.databaseName() + " does not exist on "
                    + config.databaseHost() + ". Start the server with "
                    + "drs.db.createSchema=true, which creates it, or run "
                    + "com.cqu.drs.db.SchemaTool once. Original message: " + original;
        }
        return "Could not connect to " + target + ". " + original;
    }

    /**
     * Connects, asks MySQL its version and returns it.
     *
     * Used at server startup so that the log records which database the
     * application actually reached, and used by SchemaTool to confirm the
     * connection before any schema work is attempted.
     *
     * @return the server version string reported by MySQL
     * @throws SQLException when the server cannot be reached
     */
    public String readServerVersion() throws SQLException {
        try (Connection connection = openWithoutDatabase();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT VERSION(), @@version_comment")) {
            if (results.next()) {
                return results.getString(1) + " (" + results.getString(2) + ")";
            }
            return "unknown";
        }
    }
}
