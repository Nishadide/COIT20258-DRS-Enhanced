package com.cqu.drs.db;

import com.cqu.drs.model.Department;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SeedData;
import com.cqu.drs.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Runs the schema creation on its own, without starting the server.
 *
 * Three uses:
 *
 * <ol>
 *   <li>Confirming that MySQL is reachable and the credentials are right,
 *       before anything else is blamed for a failure to start.</li>
 *   <li>Creating the database and tables once, so the server starts against a
 *       schema that already exists.</li>
 *   <li>Printing the schema as an SQL script, which the specification asks to
 *       be submitted. The script is printed from the same definitions the
 *       application executes, so the submitted script and the schema the
 *       application creates cannot drift apart. A script maintained by hand
 *       alongside code that creates the same tables is two statements of the
 *       same fact, and the two eventually disagree.</li>
 * </ol>
 *
 * Usage, from the project directory:
 *
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.cqu.drs.db.SchemaTool
 *   mvn -q compile exec:java -Dexec.mainClass=com.cqu.drs.db.SchemaTool -Dexec.args=--print-sql
 * </pre>
 */
public final class SchemaTool {

    private static final String ARG_PRINT_SQL = "--print-sql";
    private static final String ARG_DROP = "--drop-and-recreate";
    private static final String ARG_HELP = "--help";

    /** Not instantiable: this class is an entry point only. */
    private SchemaTool() {
    }

    /**
     * Entry point.
     *
     * @param args optional flags, documented by --help
     */
    public static void main(String[] args) {
        List<String> flags = List.of(args);

        if (flags.contains(ARG_HELP)) {
            printUsage();
            return;
        }

        if (flags.contains(ARG_PRINT_SQL)) {
            printSqlScript();
            return;
        }

        DatabaseConfig config = DatabaseConfig.load();
        ConnectionFactory connections = new ConnectionFactory(config);

        try {
            System.out.println("Connecting to " + config.describeTarget());
            System.out.println("MySQL reported version " + connections.readServerVersion());

            if (flags.contains(ARG_DROP)) {
                dropDatabase(connections, config.databaseName());
            }

            SchemaInitialiser.Result result = new SchemaInitialiser(connections).initialise();
            System.out.println(result.describe());
            System.out.println("Statements executed: " + result.statementsRun());
            System.out.println("Done.");

        } catch (SQLException e) {
            // The message has already been made specific by
            // ConnectionFactory.explain where the cause was a connection
            // failure, so it is printed rather than wrapped again.
            System.err.println("Schema creation failed.");
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Prints the schema as an SQL script.
     *
     * Redirect it to a file to produce the script for submission:
     *
     * <pre>
     *   mvn -q compile exec:java \
     *     -Dexec.mainClass=com.cqu.drs.db.SchemaTool \
     *     -Dexec.args=--print-sql &gt; docs/drs_enhanced_schema.sql
     * </pre>
     */
    private static void printSqlScript() {
        System.out.print(sqlScript());
    }

    /**
     * The schema as an SQL script, built from the definitions the application
     * executes at startup.
     *
     * <h2>Why this returns the script rather than printing it</h2>
     * The script is submitted as a file, and a file is a copy. A copy that
     * nothing checks is a copy that will eventually describe a schema the
     * application no longer creates, and the disagreement will be found by
     * whoever runs the script and then cannot start the application.
     *
     * <p>Returning the text means a test can generate it and compare it with
     * the committed file, so changing a table without regenerating the script
     * fails the build instead of going unnoticed. That is the only way the
     * claim in the script's own header, that it cannot differ from what the
     * application creates, is worth anything.
     *
     * @return the whole script, ending with a newline
     */
    public static String sqlScript() {
        DatabaseConfig config = DatabaseConfig.load();
        String databaseName = config.databaseName();
        String newline = System.lineSeparator();

        StringBuilder script = new StringBuilder(8192);
        script.append("-- DRS-Enhanced database schema").append(newline)
                .append("-- COIT20258 Assessment 3").append(newline)
                .append("--").append(newline)
                .append("-- Generated by com.cqu.drs.db.SchemaTool --print-sql from the")
                .append(newline)
                .append("-- same definitions the application executes at startup, so this")
                .append(newline)
                .append("-- script and the schema the application creates cannot differ.")
                .append(newline)
                .append("--").append(newline)
                .append("-- Target: MySQL 8.0 or later. Check constraints are enforced from")
                .append(newline)
                .append("-- 8.0.16 onwards; earlier versions parse and ignore them.")
                .append(newline)
                .append(newline)
                .append("CREATE DATABASE IF NOT EXISTS `").append(databaseName).append("`")
                .append(newline)
                .append("  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
                .append(newline)
                .append(newline)
                .append("USE `").append(databaseName).append("`;").append(newline)
                .append(newline);

        for (SchemaModule module : SchemaModules.all()) {
            script.append("-- ----------------------------------------"
                            + "-----------------------").append(newline)
                    .append("-- Schema module: ").append(module.name()).append(newline)
                    .append("-- ----------------------------------------"
                            + "-----------------------").append(newline)
                    .append(newline);
            for (String definition : module.tableDefinitions()) {
                script.append(definition).append(";").append(newline).append(newline);
            }
        }

        appendReferenceData(script, newline);
        return script.toString();
    }

    /**
     * Appends the rows a usable system starts with.
     *
     * <h2>Why the script populates as well as creates</h2>
     * A database with the eleven tables and nothing in them cannot be signed
     * into and cannot dispatch anything, so a marker who runs the script and
     * then starts the application would see an empty system and have no way
     * to tell whether that was the script's fault or the application's. The
     * script therefore creates the same reference data the application seeds:
     * the eight departments from the case study and the three documented
     * accounts.
     *
     * <h2>Why the same rows do not then appear twice</h2>
     * The application seeds only when the table is empty, so a database built
     * by this script is left alone by it. If both ran anyway, every statement
     * here is an upsert keyed on the natural identifier, so running the script
     * twice, or running it over a database the application has already seeded,
     * changes nothing rather than failing on a duplicate key.
     *
     * <h2>Where the values come from</h2>
     * {@link SeedData}, which is also what both department stores and both
     * entry points read. Writing the rows out here by hand would make this the
     * copy that went stale, and the symptom would be a department the
     * application expects and the database does not have.
     *
     * <p>The password hashes are computed by the model's own hashing, so an
     * account created by this script and an account created by the application
     * are byte for byte the same row. They are the documented starting
     * passwords rather than secrets, and every account is marked as needing a
     * password change.
     *
     * @param script  the script being built
     * @param newline the line separator in use
     */
    private static void appendReferenceData(StringBuilder script, String newline) {
        script.append("-- ---------------------------------------------------------------")
                .append(newline)
                .append("-- Reference data").append(newline)
                .append("--").append(newline)
                .append("-- The rows a usable system starts with. Every statement is an")
                .append(newline)
                .append("-- upsert, so running this script twice, or running it over a")
                .append(newline)
                .append("-- database the application has already seeded, changes nothing.")
                .append(newline)
                .append("-- ---------------------------------------------------------------")
                .append(newline).append(newline);

        script.append("INSERT INTO departments")
                .append(" (department_id, name, contact_email, available)").append(newline)
                .append("VALUES").append(newline);

        List<Department> departments = SeedData.departments();
        for (int i = 0; i < departments.size(); i++) {
            Department department = departments.get(i);
            script.append("  (")
                    .append(quote(department.getDepartmentId())).append(", ")
                    .append(quote(department.getName())).append(", ")
                    .append(quote(department.getContactEmail())).append(", ")
                    .append(department.isAvailable() ? "TRUE" : "FALSE")
                    .append(")")
                    .append(i == departments.size() - 1 ? "" : ",")
                    .append(newline);
        }
        script.append("ON DUPLICATE KEY UPDATE name = VALUES(name),").append(newline)
                .append("                        contact_email = VALUES(contact_email);")
                .append(newline).append(newline);

        script.append("-- The documented starting accounts. Change these passwords before")
                .append(newline)
                .append("-- the system is used for anything real: they are published in the")
                .append(newline)
                .append("-- report so that the application can be signed into.").append(newline)
                .append("INSERT INTO users").append(newline)
                .append("  (user_id, username, password_hash, password_salt,")
                .append(" password_algorithm,").append(newline)
                .append("   full_name, role, active, must_change_password,")
                .append(" department_id, coordinator_id)").append(newline)
                .append("VALUES").append(newline);

        List<SeedData.StartingAccount> accounts = SeedData.startingAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            SeedData.StartingAccount account = accounts.get(i);
            User user = account.toUser(account.seedUserId());
            script.append("  (")
                    .append(quote(user.getUserId())).append(", ")
                    .append(quote(user.getUsername())).append(", ")
                    .append(quote(user.getPasswordHash())).append(", ")
                    .append("NULL, 'SHA-256', ")
                    .append(quote(user.getFullName())).append(", ")
                    .append(quote(user.getRole().name())).append(", ")
                    .append(user.isActive() ? "TRUE" : "FALSE").append(", ")
                    .append(user.isMustChangePassword() ? "TRUE" : "FALSE").append(", ")
                    .append(quote(departmentOf(user))).append(", ")
                    .append(quote(coordinatorOf(user)))
                    .append(")")
                    .append(i == accounts.size() - 1 ? "" : ",")
                    .append(newline);
        }
        script.append("ON DUPLICATE KEY UPDATE full_name = VALUES(full_name),").append(newline)
                .append("                        role = VALUES(role);")
                .append(newline).append(newline);
    }

    private static String departmentOf(User user) {
        return user instanceof DepartmentResponder responder
                ? responder.getDepartmentId() : null;
    }

    private static String coordinatorOf(User user) {
        return user instanceof EmergencyCoordinator coordinator
                ? coordinator.getCoordinatorId() : null;
    }

    /**
     * Quotes a value for the script, or writes NULL when there is none.
     *
     * The values come from {@link SeedData} and contain nothing that needs
     * escaping today. The escaping is here because a script is read as a
     * promise that it can be run as it stands, and a department name with an
     * apostrophe in it added later would otherwise produce a script that stops
     * halfway with a syntax error.
     *
     * @param value the value, which may be null
     * @return the SQL literal
     */
    private static String quote(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /**
     * Drops the database so the next run recreates it empty.
     *
     * Present because rebuilding from nothing is needed repeatedly while the
     * data migration is being written. It is behind an explicit flag and is
     * never called by the server.
     *
     * @param connections  where connections come from
     * @param databaseName the database to drop
     * @throws SQLException when the statement is rejected
     */
    private static void dropDatabase(ConnectionFactory connections, String databaseName)
            throws SQLException {
        System.out.println("Dropping database " + databaseName
                + ". All data in it will be lost.");
        try (Connection connection = connections.openWithoutDatabase();
             Statement statement = connection.createStatement()) {
            // The name is validated by DatabaseConfig.databaseName and
            // backtick quoted, for the reason given in SchemaInitialiser.
            statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
        }
        System.out.println("Dropped.");
    }

    private static void printUsage() {
        System.out.println("""
                SchemaTool creates the DRS-Enhanced database and tables.

                  (no flags)            Create the database and any missing tables.
                                        Safe to run repeatedly: nothing already
                                        present is altered.

                  --print-sql           Print the schema as an SQL script and exit
                                        without connecting to MySQL.

                  --drop-and-recreate   Drop the database, then create it empty.
                                        Destroys all data. Used while developing
                                        the data migration.

                  --help                Show this text.

                Connection settings are read from db.properties, and each can be
                overridden with a system property such as -Ddrs.db.password=secret
                or an environment variable such as DRS_DB_PASSWORD.""");
    }
}
