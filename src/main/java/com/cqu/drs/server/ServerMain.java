package com.cqu.drs.server;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.db.SchemaInitialiser;
import com.cqu.drs.repository.MySqlRequestLogRepository;
import com.cqu.drs.repository.RequestLogRepository;
import com.cqu.drs.server.handler.PingHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Starts the DRS-Enhanced application server.
 *
 * Run it from the project directory with:
 *
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.cqu.drs.server.ServerMain
 * </pre>
 *
 * or in NetBeans by right-clicking this file and choosing Run File.
 *
 * <p>The server checks the database before it opens its port. Binding first
 * and failing on the first request would tell a client the server was ready
 * when it was not, and the person would see the fault one step away from its
 * cause.
 */
public final class ServerMain {

    /** Not instantiable: this class is an entry point only. */
    private ServerMain() {
    }

    /**
     * Entry point.
     *
     * @param args ignored. Everything is configured through db.properties,
     *             system properties and environment variables
     */
    public static void main(String[] args) {
        DatabaseConfig config = DatabaseConfig.load();
        ConnectionFactory connections = new ConnectionFactory(config);

        try {
            log("Connecting to " + config.describeTarget());
            log("MySQL reported version " + connections.readServerVersion());

            if (config.createSchemaOnStartup()) {
                SchemaInitialiser.Result schema =
                        new SchemaInitialiser(connections).initialise();
                log(schema.describe());
            } else {
                log("Schema creation is switched off, so the database is used as it is.");
            }

        } catch (SQLException e) {
            // ConnectionFactory has already turned the common causes into
            // messages that say what to do, so the message is printed as it
            // is rather than wrapped in another one.
            System.err.println("The server cannot start because the database is "
                    + "not usable.");
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        RequestLogRepository requestLog = new MySqlRequestLogRepository(connections);
        SessionRegistry sessions = new SessionRegistry();

        RequestDispatcher dispatcher = new RequestDispatcher(sessions, requestLog)
                .register(new PingHandler());

        DrsServer server = new DrsServer(config.serverPort(), config.serverThreads(), dispatcher);

        // Registered before the server starts, so that stopping the process
        // with Ctrl+C closes the port and the client threads rather than
        // leaving the port held until the operating system releases it.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Stopping.");
            server.stop();
        }, "drs-shutdown"));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("The server could not listen on port "
                    + config.serverPort() + ". " + e.getMessage());
            System.err.println("Another program may be using that port. Change it with "
                    + "-Ddrs.server.port=5051 or in db.properties.");
            System.exit(1);
            return;
        }

        log("Listening on port " + server.getPort()
                + ", serving up to " + server.getPoolSize() + " clients at once.");
        log(dispatcher.handlerCount() + " operations are available.");
        log("Press Ctrl+C to stop.");

        // The accept loop runs on its own thread, so this thread would
        // otherwise finish and, with every other thread a daemon, take the JVM
        // with it.
        keepRunning();
    }

    private static void keepRunning() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println(DateTimeFormatter.ISO_LOCAL_TIME.format(
                LocalDateTime.now().toLocalTime().withNano(0)) + "  " + message);
    }
}
