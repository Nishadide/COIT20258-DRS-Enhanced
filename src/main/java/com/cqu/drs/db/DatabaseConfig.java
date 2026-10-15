package com.cqu.drs.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Settings for the server, the client and the database connection.
 *
 * Nothing in DRS-Enhanced hardcodes a host, port, database name or password.
 * A value is looked up in four places and the first one found is used:
 *
 * <ol>
 *   <li>a JVM system property, such as {@code -Ddrs.db.password=secret}</li>
 *   <li>an environment variable, such as {@code DRS_DB_PASSWORD}</li>
 *   <li>{@code db.properties} on the classpath</li>
 *   <li>the default passed to the getter</li>
 * </ol>
 *
 * The override order exists so that the application can be run on a machine
 * whose MySQL password differs from the committed default without editing a
 * tracked file. Committing a working password would be the alternative, and
 * that is worse.
 */
public final class DatabaseConfig {

    /** Name of the properties file looked for on the classpath. */
    private static final String PROPERTIES_FILE = "/db.properties";

    // Property keys. Declared as constants so a typo is a compile error
    // rather than a silently missing setting.
    public static final String KEY_SERVER_HOST = "drs.server.host";
    public static final String KEY_SERVER_PORT = "drs.server.port";
    public static final String KEY_SERVER_THREADS = "drs.server.threads";
    public static final String KEY_DB_HOST = "drs.db.host";
    public static final String KEY_DB_PORT = "drs.db.port";
    public static final String KEY_DB_NAME = "drs.db.name";
    public static final String KEY_DB_USER = "drs.db.user";
    public static final String KEY_DB_PASSWORD = "drs.db.password";
    public static final String KEY_DB_PARAMS = "drs.db.params";
    public static final String KEY_CREATE_SCHEMA = "drs.db.createSchema";
    public static final String KEY_CLIENT_MODE = "drs.client.mode";

    private static final String DEFAULT_SERVER_HOST = "localhost";
    private static final int DEFAULT_SERVER_PORT = 5050;
    private static final int DEFAULT_SERVER_THREADS = 32;
    private static final String DEFAULT_DB_HOST = "localhost";
    private static final int DEFAULT_DB_PORT = 3306;
    private static final String DEFAULT_DB_NAME = "drs_enhanced";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASSWORD = "";
    private static final String DEFAULT_DB_PARAMS =
            "sslMode=DISABLED&allowPublicKeyRetrieval=true"
            + "&characterEncoding=UTF-8&rewriteBatchedStatements=true";

    private final Properties fileProperties;

    /**
     * Reads db.properties from the classpath. A missing file is not an error,
     * because every setting has a default and can be supplied as a system
     * property or an environment variable instead.
     */
    private DatabaseConfig() {
        this.fileProperties = new Properties();
        try (InputStream in = DatabaseConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (in != null) {
                fileProperties.load(in);
            }
        } catch (IOException e) {
            // Deliberately not fatal. The defaults and the overrides remain
            // available, so the application can still start.
            System.err.println("Warning: " + PROPERTIES_FILE
                    + " could not be read, using defaults. " + e.getMessage());
        }
    }

    /**
     * Loads the configuration.
     *
     * @return a configuration reflecting the current properties, environment
     *         and file
     */
    public static DatabaseConfig load() {
        return new DatabaseConfig();
    }

    /**
     * The value of a setting, resolved through the four sources in order.
     *
     * @param key          the property key, such as drs.db.host
     * @param defaultValue returned when no source holds the key
     * @return the resolved value
     */
    public String get(String key, String defaultValue) {
        String fromSystem = System.getProperty(key);
        if (isPresent(fromSystem)) {
            return fromSystem.trim();
        }

        String fromEnvironment = System.getenv(toEnvironmentVariable(key));
        if (isPresent(fromEnvironment)) {
            return fromEnvironment.trim();
        }

        String fromFile = fileProperties.getProperty(key);
        // A key present in the file but left blank is a deliberate empty
        // value, such as a blank MySQL password, so it is returned as given
        // rather than falling through to the default.
        if (fromFile != null) {
            return fromFile.trim();
        }

        return defaultValue;
    }

    /**
     * The value of a setting that must be a whole number.
     *
     * @param key          the property key
     * @param defaultValue returned when no source holds the key
     * @return the resolved value
     * @throws IllegalStateException when the value present is not a number,
     *         because starting with a silently wrong port is worse than
     *         refusing to start
     */
    public int getInt(String key, int defaultValue) {
        String value = get(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    key + " must be a whole number but was \"" + value + "\".", e);
        }
    }

    /**
     * The value of a setting that must be true or false.
     *
     * @param key          the property key
     * @param defaultValue returned when no source holds the key
     * @return the resolved value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public String serverHost() {
        return get(KEY_SERVER_HOST, DEFAULT_SERVER_HOST);
    }

    public int serverPort() {
        int port = getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(
                    KEY_SERVER_PORT + " must be between 1 and 65535 but was " + port + ".");
        }
        return port;
    }

    public int serverThreads() {
        int threads = getInt(KEY_SERVER_THREADS, DEFAULT_SERVER_THREADS);
        if (threads < 1) {
            throw new IllegalStateException(
                    KEY_SERVER_THREADS + " must be at least 1 but was " + threads + ".");
        }
        return threads;
    }

    public String databaseHost() {
        return get(KEY_DB_HOST, DEFAULT_DB_HOST);
    }

    public int databasePort() {
        return getInt(KEY_DB_PORT, DEFAULT_DB_PORT);
    }

    public String databaseName() {
        String name = get(KEY_DB_NAME, DEFAULT_DB_NAME);
        // The name is concatenated into a CREATE DATABASE statement, which
        // cannot take a parameter marker, so it is checked rather than
        // trusted. See SchemaInitialiser.
        if (!name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException(KEY_DB_NAME
                    + " may contain only letters, digits and underscores but was \""
                    + name + "\".");
        }
        return name;
    }

    public String databaseUser() {
        return get(KEY_DB_USER, DEFAULT_DB_USER);
    }

    public String databasePassword() {
        return get(KEY_DB_PASSWORD, DEFAULT_DB_PASSWORD);
    }

    public String databaseParameters() {
        return get(KEY_DB_PARAMS, DEFAULT_DB_PARAMS);
    }

    public boolean createSchemaOnStartup() {
        return getBoolean(KEY_CREATE_SCHEMA, true);
    }

    /**
     * Whether the client reaches its data through the server or directly.
     *
     * Two values are accepted. "local" is the Assessment 2 arrangement, where
     * the application holds its own repositories and needs nothing else
     * running. "remote" is the three-tier arrangement, where the client holds
     * no repositories and asks the server for everything.
     *
     * The setting exists because the move from one to the other is made one
     * operation at a time. Without it, the application would have to be broken
     * for as long as the move took, and nobody could demonstrate anything in
     * between.
     *
     * @return true when the client should talk to the server
     */
    public boolean isRemoteClient() {
        return "remote".equalsIgnoreCase(clientMode());
    }

    /**
     * The client mode as written, for reporting in a log or a status line.
     *
     * @return "local" or "remote"
     * @throws IllegalStateException when the value is neither, because
     *         starting in the wrong mode is worse than refusing to start
     */
    public String clientMode() {
        String mode = get(KEY_CLIENT_MODE, "local");
        if (!"local".equalsIgnoreCase(mode) && !"remote".equalsIgnoreCase(mode)) {
            throw new IllegalStateException(KEY_CLIENT_MODE
                    + " must be either local or remote, but was \"" + mode + "\".");
        }
        return mode.toLowerCase();
    }

    /**
     * The JDBC URL of the DRS database.
     *
     * @return a URL naming the database
     */
    public String jdbcUrl() {
        return baseUrl() + databaseName() + "?" + databaseParameters();
    }

    /**
     * The JDBC URL of the MySQL server without naming a database.
     *
     * This is needed because the database itself may not exist yet, and a
     * connection that names a database that has not been created fails. The
     * schema initialiser connects with this URL, issues CREATE DATABASE, then
     * reconnects with {@link #jdbcUrl()}.
     *
     * @return a URL naming the server only
     */
    public String jdbcUrlWithoutDatabase() {
        return baseUrl() + "?" + databaseParameters();
    }

    private String baseUrl() {
        return "jdbc:mysql://" + databaseHost() + ":" + databasePort() + "/";
    }

    /**
     * A one line description of where the server will connect, safe to print
     * at startup because it never includes the password.
     *
     * @return a description of the connection target
     */
    public String describeTarget() {
        return databaseUser() + "@" + databaseHost() + ":" + databasePort()
                + "/" + databaseName();
    }

    /**
     * Converts a property key into the environment variable that overrides it,
     * so drs.db.password is overridden by DRS_DB_PASSWORD.
     *
     * @param key the property key
     * @return the environment variable name
     */
    private static String toEnvironmentVariable(String key) {
        return key.toUpperCase().replace('.', '_');
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
