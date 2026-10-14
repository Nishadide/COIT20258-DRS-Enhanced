package com.cqu.drs.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T106 to T114: resolving configuration.
 *
 * These need no database. They test the order in which the four sources of a
 * setting are consulted, which is the part that decides whether a marker can
 * run the application on a machine whose MySQL password differs from the
 * committed default.
 */
@DisplayName("DatabaseConfig")
class DatabaseConfigTest {

    /**
     * Every key a test sets, cleared afterwards. A system property left behind
     * would change the result of a later test in the same JVM, and the failure
     * would appear to come from the later test.
     */
    private static final String[] KEYS_USED = {
            DatabaseConfig.KEY_DB_HOST,
            DatabaseConfig.KEY_DB_PORT,
            DatabaseConfig.KEY_DB_NAME,
            DatabaseConfig.KEY_DB_USER,
            DatabaseConfig.KEY_DB_PASSWORD,
            DatabaseConfig.KEY_DB_PARAMS,
            DatabaseConfig.KEY_SERVER_PORT,
            DatabaseConfig.KEY_SERVER_THREADS,
            DatabaseConfig.KEY_CREATE_SCHEMA
    };

    @AfterEach
    void clearOverrides() {
        for (String key : KEYS_USED) {
            System.clearProperty(key);
        }
    }

    @Test
    @DisplayName("T106: a system property overrides the value in db.properties")
    void systemPropertyWinsOverFile() {
        System.setProperty(DatabaseConfig.KEY_DB_HOST, "192.168.1.50");
        assertEquals("192.168.1.50", DatabaseConfig.load().databaseHost());
    }

    @Test
    @DisplayName("T107: with no override, the value comes from db.properties")
    void fileSuppliesValueWhenNoOverride() {
        // db.properties on the test classpath sets these, so the defaults
        // built into the class are not what is being read here.
        DatabaseConfig config = DatabaseConfig.load();
        assertEquals("localhost", config.databaseHost());
        assertEquals("drs_enhanced", config.databaseName());
    }

    @Test
    @DisplayName("T108: a blank password in the file is honoured rather than replaced by a default")
    void blankPasswordIsADeliberateValue() {
        // A standard MySQL installation may have no root password. If a blank
        // value fell through to a default the application would send the
        // wrong credentials and the failure would look like a wrong password.
        assertEquals("", DatabaseConfig.load().databasePassword());
    }

    @Test
    @DisplayName("T109: the JDBC URL names host, port, database and parameters")
    void jdbcUrlIsAssembledFromTheParts() {
        System.setProperty(DatabaseConfig.KEY_DB_HOST, "db.example.edu");
        System.setProperty(DatabaseConfig.KEY_DB_PORT, "3307");
        System.setProperty(DatabaseConfig.KEY_DB_NAME, "drs_test");
        System.setProperty(DatabaseConfig.KEY_DB_PARAMS, "sslMode=DISABLED");

        assertEquals("jdbc:mysql://db.example.edu:3307/drs_test?sslMode=DISABLED",
                DatabaseConfig.load().jdbcUrl());
    }

    @Test
    @DisplayName("T110: the URL used before the database exists names no database")
    void jdbcUrlWithoutDatabaseOmitsTheName() {
        System.setProperty(DatabaseConfig.KEY_DB_HOST, "localhost");
        System.setProperty(DatabaseConfig.KEY_DB_PORT, "3306");
        System.setProperty(DatabaseConfig.KEY_DB_NAME, "drs_test");
        System.setProperty(DatabaseConfig.KEY_DB_PARAMS, "sslMode=DISABLED");

        String url = DatabaseConfig.load().jdbcUrlWithoutDatabase();

        assertEquals("jdbc:mysql://localhost:3306/?sslMode=DISABLED", url);
        assertTrue(!url.contains("drs_test"),
                "The schema initialiser connects with this URL before the "
                        + "database exists, so naming it would fail.");
    }

    @Test
    @DisplayName("T111: a database name containing anything but letters, digits and underscores is refused")
    void databaseNameIsValidated() {
        // The name is concatenated into CREATE DATABASE, which cannot take a
        // parameter marker, so this check is the guard against injection
        // through a configuration file.
        System.setProperty(DatabaseConfig.KEY_DB_NAME, "drs`; DROP DATABASE mysql; --");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.load().databaseName());
        assertTrue(refused.getMessage().contains(DatabaseConfig.KEY_DB_NAME));
    }

    @Test
    @DisplayName("T112: a valid database name with an underscore is accepted")
    void databaseNameWithUnderscoreAccepted() {
        System.setProperty(DatabaseConfig.KEY_DB_NAME, "drs_enhanced_2026");
        assertEquals("drs_enhanced_2026", DatabaseConfig.load().databaseName());
    }

    @Test
    @DisplayName("T113: a port that is not a number is refused rather than silently defaulted")
    void nonNumericPortIsRefused() {
        System.setProperty(DatabaseConfig.KEY_SERVER_PORT, "five thousand");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.load().serverPort());
        assertTrue(refused.getMessage().contains("whole number"));
    }

    @Test
    @DisplayName("T114: a port outside 1 to 65535 is refused")
    void portOutOfRangeIsRefused() {
        System.setProperty(DatabaseConfig.KEY_SERVER_PORT, "70000");
        assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.load().serverPort());

        System.setProperty(DatabaseConfig.KEY_SERVER_PORT, "0");
        assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.load().serverPort());
    }

    @Test
    @DisplayName("A thread count below one is refused")
    void threadCountBelowOneIsRefused() {
        System.setProperty(DatabaseConfig.KEY_SERVER_THREADS, "0");
        assertThrows(IllegalStateException.class,
                () -> DatabaseConfig.load().serverThreads());
    }

    @Test
    @DisplayName("createSchema reads as a boolean and defaults to true")
    void createSchemaIsBoolean() {
        assertTrue(DatabaseConfig.load().createSchemaOnStartup());

        System.setProperty(DatabaseConfig.KEY_CREATE_SCHEMA, "false");
        assertTrue(!DatabaseConfig.load().createSchemaOnStartup());
    }

    @Test
    @DisplayName("The connection target can be described without revealing the password")
    void describeTargetHidesThePassword() {
        System.setProperty(DatabaseConfig.KEY_DB_USER, "drs");
        System.setProperty(DatabaseConfig.KEY_DB_PASSWORD, "s3cret");
        System.setProperty(DatabaseConfig.KEY_DB_HOST, "localhost");
        System.setProperty(DatabaseConfig.KEY_DB_NAME, "drs_enhanced");

        String description = DatabaseConfig.load().describeTarget();

        assertTrue(description.contains("drs@localhost"));
        assertTrue(!description.contains("s3cret"),
                "The description is printed to the log at startup, so it must "
                        + "never carry the password.");
    }
}
