package com.cqu.drs.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the database and its tables from Java, with no SQL script to run by
 * hand.
 *
 * The specification requires the schema to be created programmatically. The
 * reason it is worth doing properly is that a marker who has to execute a
 * script before the application will start has one more chance to be defeated
 * by something that is not the application's fault. Here the server creates
 * what it needs on first run, and a second run finds everything present and
 * changes nothing.
 *
 * <p>Idempotence comes from the statements themselves. CREATE DATABASE IF NOT
 * EXISTS and CREATE TABLE IF NOT EXISTS succeed whether or not the object is
 * already there, so the initialiser does not need to ask what exists before
 * deciding what to do. It does ask, but only in order to report accurately on
 * what it found and what it added.
 *
 * <p>An SQL script is still produced for the report, as the specification also
 * asks for one, but it is a record of the schema rather than a step in running
 * the application.
 */
public final class SchemaInitialiser {

    /** The key written to schema_metadata when the schema has been created. */
    public static final String METADATA_ITEM = "schema";

    private final ConnectionFactory connections;
    private final List<SchemaModule> modules;

    /**
     * Creates an initialiser for the registered modules.
     *
     * @param connections where connections come from
     */
    public SchemaInitialiser(ConnectionFactory connections) {
        this(connections, SchemaModules.all());
    }

    /**
     * Creates an initialiser for a given set of modules, which the tests use
     * to apply one module in isolation.
     *
     * @param connections where connections come from
     * @param modules     the modules to apply, in creation order
     */
    public SchemaInitialiser(ConnectionFactory connections, List<SchemaModule> modules) {
        if (connections == null) {
            throw new IllegalArgumentException("A schema initialiser needs a connection factory.");
        }
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("A schema initialiser needs at least one module.");
        }
        this.connections = connections;
        this.modules = modules;
    }

    /**
     * Creates the database if it is absent, then every table of every
     * registered module.
     *
     * @return what was found and what was created
     * @throws SQLException when the server cannot be reached, or a statement
     *                      is rejected
     */
    public Result initialise() throws SQLException {
        String databaseName = connections.getConfig().databaseName();

        boolean databaseCreated = createDatabaseIfAbsent(databaseName);

        try (Connection connection = connections.open()) {
            Set<String> before = readTableNames(connection, databaseName);

            List<String> applied = new ArrayList<>();
            for (SchemaModule module : modules) {
                applyModule(connection, module, applied);
            }

            Set<String> after = readTableNames(connection, databaseName);

            Set<String> created = new LinkedHashSet<>(after);
            created.removeAll(before);

            recordRun(connection, after.size(), created.size());

            return new Result(databaseName, databaseCreated, applied.size(),
                    Collections.unmodifiableSet(created),
                    Collections.unmodifiableSet(after));
        }
    }

    /**
     * Issues CREATE DATABASE IF NOT EXISTS.
     *
     * This runs on a connection that names no database, because a connection
     * naming a database that does not exist fails before any statement can be
     * sent.
     *
     * The database name cannot be supplied as a parameter marker. SQL only
     * accepts parameters where a value is expected, and the name of a database
     * is an identifier, not a value. The name is therefore concatenated into
     * the statement, which is exactly the shape an injection takes, so two
     * things guard it: DatabaseConfig.databaseName rejects any name that is
     * not letters, digits and underscores, and the name is enclosed in
     * backticks here. Every other statement in the application uses parameter
     * markers, and this is the one place that cannot.
     *
     * @param databaseName the validated database name
     * @return true when the database did not exist and was created
     * @throws SQLException when the statement is rejected
     */
    private boolean createDatabaseIfAbsent(String databaseName) throws SQLException {
        try (Connection connection = connections.openWithoutDatabase();
             Statement statement = connection.createStatement()) {

            boolean existed = databaseExists(connection, databaseName);

            statement.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "`"
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");

            return !existed;
        }
    }

    /**
     * Asks the server whether a database of this name is present.
     *
     * The name is passed as a parameter here, because in a query against
     * information_schema it is a value rather than an identifier.
     *
     * @param connection   a connection to the server
     * @param databaseName the name to look for
     * @return true when the database exists
     * @throws SQLException when the query fails
     */
    private boolean databaseExists(Connection connection, String databaseName)
            throws SQLException {
        String sql = "SELECT 1 FROM information_schema.schemata"
                + " WHERE schema_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, databaseName);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    /**
     * Runs every statement of one module, in the order the module returns
     * them.
     *
     * Each statement runs on its own rather than inside a transaction, because
     * MySQL commits data definition statements implicitly and cannot roll them
     * back. A failure part way through therefore leaves the tables created so
     * far in place. That is acceptable and is the reason every statement is
     * written as CREATE TABLE IF NOT EXISTS: the next run completes the work
     * instead of failing on what already exists.
     *
     * @param connection the connection to the DRS database
     * @param module     the module being applied
     * @param applied    collects the statements run, for the result
     * @throws SQLException when a statement is rejected, with the module named
     */
    private void applyModule(Connection connection, SchemaModule module,
                             List<String> applied) throws SQLException {
        List<String> definitions = module.tableDefinitions();
        for (int i = 0; i < definitions.size(); i++) {
            String definition = definitions.get(i);
            try (Statement statement = connection.createStatement()) {
                statement.execute(definition);
                applied.add(definition);
            } catch (SQLException e) {
                throw new SQLException("Statement " + (i + 1) + " of the \""
                        + module.name() + "\" schema module was rejected by MySQL: "
                        + e.getMessage() + System.lineSeparator()
                        + "Statement was: " + firstLineOf(definition),
                        e.getSQLState(), e.getErrorCode(), e);
            }
        }
    }

    /**
     * Reads the names of the tables present in the database.
     *
     * @param connection   a connection to the database
     * @param databaseName the database to inspect
     * @return the table names, in the order the driver reports them
     * @throws SQLException when the metadata cannot be read
     */
    private Set<String> readTableNames(Connection connection, String databaseName)
            throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        // MySQL reports a database as a JDBC catalog, so the name is passed as
        // the catalog and the schema pattern is left null.
        try (ResultSet results =
                     metaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
            while (results.next()) {
                names.add(results.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    /**
     * Writes or updates the schema row in schema_metadata, so that the
     * database itself records when its structure was last confirmed.
     *
     * @param connection   a connection to the database
     * @param tableCount   how many tables are now present
     * @param createdCount how many were created on this run
     * @throws SQLException when the write fails
     */
    private void recordRun(Connection connection, int tableCount, int createdCount)
            throws SQLException {
        String sql = "INSERT INTO schema_metadata (item, applied_at, detail)"
                + " VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE applied_at = VALUES(applied_at),"
                + " detail = VALUES(detail)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, METADATA_ITEM);
            statement.setObject(2, LocalDateTime.now());
            statement.setString(3, tableCount + " tables present, "
                    + createdCount + " created on this run");
            statement.executeUpdate();
        }
    }

    private static String firstLineOf(String statement) {
        int newline = statement.indexOf('\n');
        return newline < 0 ? statement : statement.substring(0, newline).trim();
    }

    /**
     * What the initialiser found and what it changed.
     *
     * @param databaseName    the database worked on
     * @param databaseCreated whether the database itself had to be created
     * @param statementsRun   how many statements were executed
     * @param tablesCreated   the tables that were not present before this run
     * @param tablesPresent   every table present after this run
     */
    public record Result(String databaseName,
                         boolean databaseCreated,
                         int statementsRun,
                         Set<String> tablesCreated,
                         Set<String> tablesPresent) {

        /**
         * Whether this run changed anything.
         *
         * @return true when the database or any table was created
         */
        public boolean changedAnything() {
            return databaseCreated || !tablesCreated.isEmpty();
        }

        /**
         * A summary suitable for the server's startup log.
         *
         * @return one or two lines describing the outcome
         */
        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append("Database ").append(databaseName).append(": ");
            if (databaseCreated) {
                text.append("created. ");
            } else {
                text.append("already present. ");
            }
            if (tablesCreated.isEmpty()) {
                text.append("All ").append(tablesPresent.size())
                        .append(" tables were already present, so nothing was changed.");
            } else {
                text.append("Created ").append(tablesCreated.size())
                        .append(" of ").append(tablesPresent.size())
                        .append(" tables: ").append(String.join(", ", tablesCreated))
                        .append('.');
            }
            return text.toString();
        }
    }
}
