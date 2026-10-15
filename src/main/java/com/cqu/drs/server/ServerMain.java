package com.cqu.drs.server;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.db.SchemaInitialiser;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.MySqlAlertRepository;
import com.cqu.drs.repository.MySqlAuditRepository;
import com.cqu.drs.repository.MySqlDepartmentRepository;
import com.cqu.drs.repository.MySqlDisasterReportRepository;
import com.cqu.drs.repository.MySqlUserRepository;
import com.cqu.drs.repository.UserRepository;
import com.cqu.drs.repository.MySqlRequestLogRepository;
import com.cqu.drs.repository.RequestLogRepository;
import com.cqu.drs.server.handler.FindReportByReferenceHandler;
import com.cqu.drs.server.handler.PingHandler;
import com.cqu.drs.server.handler.SubmitReportHandler;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.ReportOperations;
import com.cqu.drs.service.ReportService;

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

        // The data tier, as the server sees it. These are the MySQL
        // implementations; the client has none of this.
        DisasterReportRepository reportRepository =
                new MySqlDisasterReportRepository(connections);
        UserRepository userRepository = new MySqlUserRepository(connections);
        DepartmentRepository departmentRepository =
                new MySqlDepartmentRepository(connections);
        AlertRepository alertRepository = new MySqlAlertRepository(connections);
        AuditRepository auditRepository = new MySqlAuditRepository(connections);

        AuditService auditService = new AuditService(auditRepository);
        AccountService accountService = new AccountService(userRepository, auditService);

        if (departmentRepository.seedIfEmpty()) {
            log("Seeded the eight responding departments.");
        }
        seedAccountsIfNone(accountService);

        // The same ReportService the desktop application used in Assessment 2,
        // unchanged, now running on the server with a MySQL repository behind
        // it. That it needed no modification is the point: the business rules
        // were never tied to how the data was stored.
        ReportOperations reportService = new ReportService(reportRepository, auditService);

        RequestDispatcher dispatcher = new RequestDispatcher(sessions, requestLog)
                .register(new PingHandler())
                .register(new SubmitReportHandler(reportService))
                .register(new FindReportByReferenceHandler(reportService));

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

    /**
     * Creates the starting accounts when the database holds none.
     *
     * Without this, a server started against a fresh database would be running
     * correctly with nobody able to sign in, and the first thing anyone did
     * would be to conclude the application was broken.
     *
     * The same three accounts DRS-Initial seeded, so the documented logins
     * continue to work.
     *
     * @param accountService the service that creates accounts
     */
    private static void seedAccountsIfNone(AccountService accountService) {
        if (!accountService.findAll().isEmpty()) {
            return;
        }
        accountService.create("admin", "admin123", "System Administrator",
                UserRole.ADMINISTRATOR, null, "system");
        accountService.create("coordinator", "coord123", "Emergency Coordinator",
                UserRole.COORDINATOR, null, "system");
        accountService.create("fire.crew", "crew1234", "Fire and Emergency Crew",
                UserRole.RESPONDER, "DEP-01", "system");
        log("Seeded the three starting accounts: admin, coordinator, fire.crew.");
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
