package com.cqu.drs.server;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.db.SchemaInitialiser;
import com.cqu.drs.migration.DataMigrationService;
import com.cqu.drs.migration.JsonMigrationSource;
import com.cqu.drs.migration.MigrationLedger;
import com.cqu.drs.migration.MigrationSource;
import com.cqu.drs.migration.MySqlMigrationLedger;
import com.cqu.drs.model.SeedData;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.MySqlAlertRepository;
import com.cqu.drs.repository.MySqlAuditRepository;
import com.cqu.drs.repository.MySqlDepartmentRepository;
import com.cqu.drs.repository.MySqlDisasterReportRepository;
import com.cqu.drs.repository.MySqlRequestLogRepository;
import com.cqu.drs.repository.MySqlUserRepository;
import com.cqu.drs.repository.RequestLogRepository;
import com.cqu.drs.repository.UserRepository;
import com.cqu.drs.server.handler.ActivateAccountHandler;
import com.cqu.drs.server.handler.AllocateHandler;
import com.cqu.drs.server.handler.AssessHandler;
import com.cqu.drs.server.handler.AssignRoleHandler;
import com.cqu.drs.server.handler.AvailableDepartmentsHandler;
import com.cqu.drs.server.handler.AwaitingAssessmentHandler;
import com.cqu.drs.server.handler.ChangePasswordHandler;
import com.cqu.drs.server.handler.CreateAccountHandler;
import com.cqu.drs.server.handler.DeactivateAccountHandler;
import com.cqu.drs.server.handler.FindAllAccountsHandler;
import com.cqu.drs.server.handler.FindAllAlertsHandler;
import com.cqu.drs.server.handler.FindAllAuditHandler;
import com.cqu.drs.server.handler.FindAllDepartmentsHandler;
import com.cqu.drs.server.handler.FindAllReportsHandler;
import com.cqu.drs.server.handler.FindReportByReferenceHandler;
import com.cqu.drs.server.handler.IssueAlertHandler;
import com.cqu.drs.server.handler.LoginHandler;
import com.cqu.drs.server.handler.LogoutHandler;
import com.cqu.drs.server.handler.MigrateHandler;
import com.cqu.drs.server.handler.NearbyReportsHandler;
import com.cqu.drs.server.handler.PingHandler;
import com.cqu.drs.server.handler.QueueByPriorityHandler;
import com.cqu.drs.server.handler.ReportForTaskHandler;
import com.cqu.drs.server.handler.ResolveReportHandler;
import com.cqu.drs.server.handler.SetDepartmentAvailabilityHandler;
import com.cqu.drs.server.handler.SubmitReportHandler;
import com.cqu.drs.server.handler.TasksForDepartmentHandler;
import com.cqu.drs.server.handler.UpdateStatusHandler;
import com.cqu.drs.server.handler.VerifyChainHandler;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AlertService;
import com.cqu.drs.service.AssessmentService;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.AuthenticationService;
import com.cqu.drs.service.CoordinationService;
import com.cqu.drs.service.DepartmentService;
import com.cqu.drs.service.ReportService;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
 *
 * <h2>This class is the server's wiring, and the client has its own</h2>
 * The services built here are the same classes the desktop application used in
 * Assessment 2, with the MySQL repositories behind them instead of the JSON
 * ones. That they needed no modification is the point: the business rules were
 * never tied to how the data was stored, so moving the storage into a database
 * and the rules onto a server changed neither.
 *
 * <p>{@code AppContext} does the equivalent job in the client and shares
 * nothing with this method. A client that could reach these objects would be a
 * client with a database connection, which is the arrangement three tiers
 * exists to end.
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

        // The business tier. Every service is the one from Assessment 2.
        AuditService auditService = new AuditService(auditRepository);
        AccountService accountService = new AccountService(userRepository, auditService);
        AuthenticationService authenticationService =
                new AuthenticationService(userRepository, auditService);
        ReportService reportService = new ReportService(reportRepository, auditService);
        AssessmentService assessmentService =
                new AssessmentService(reportRepository, auditService);
        CoordinationService coordinationService = new CoordinationService(
                reportRepository, departmentRepository, auditService);
        AlertService alertService =
                new AlertService(reportRepository, alertRepository, auditService);
        DepartmentService departmentService =
                new DepartmentService(departmentRepository, auditService);

        // The migration reads the Assessment 2 files and writes through the
        // same repositories as everything else, so the migrated rows are
        // written under the constraints and transactions the application
        // relies on rather than around them.
        MigrationSource migrationSource =
                new JsonMigrationSource(Path.of(config.migrationSourceDirectory()));
        MigrationLedger migrationLedger = new MySqlMigrationLedger(connections);
        DataMigrationService migrationService = new DataMigrationService(
                migrationSource, departmentRepository, userRepository,
                reportRepository, alertRepository, auditRepository, migrationLedger);

        if (departmentRepository.seedIfEmpty()) {
            log("Seeded the eight responding departments.");
        }
        seedAccountsIfNone(accountService);

        RequestDispatcher dispatcher = buildDispatcher(sessions, requestLog,
                new ServerServices(authenticationService, reportService,
                        assessmentService, coordinationService, alertService,
                        accountService, auditService, departmentService,
                        migrationService));

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
        log(dispatcher.handlerCount() + " of " + Operation.values().length
                + " operations are available.");
        log("Migration source: " + migrationSource.describe());
        reportUnhandled(dispatcher);
        log("Press Ctrl+C to stop.");

        // The accept loop runs on its own thread, so this thread would
        // otherwise finish and, with every other thread a daemon, take the JVM
        // with it.
        keepRunning();
    }

    /**
     * Registers every handler the protocol has one for.
     *
     * <h2>One list, used by the server and by the tests</h2>
     * This is a method rather than a block inside {@code main} so that a test
     * can build the same dispatcher over JSON repositories and ask it whether
     * any declared operation has been left without a handler. A second list in
     * a test would answer that question about itself.
     *
     * <p>The handlers are grouped the way PROTOCOL.md groups the operations,
     * so that the document and this method can be read side by side. Each is
     * constructed with the services it needs and nothing else: a handler that
     * could reach every service would be a handler whose dependencies say
     * nothing about what it does.
     *
     * @param sessions   where sessions are opened and found
     * @param requestLog where every request is recorded
     * @param services   the services the handlers work through
     * @return the configured dispatcher
     */
    public static RequestDispatcher buildDispatcher(SessionRegistry sessions,
                                                    RequestLogRepository requestLog,
                                                    ServerServices services) {
        return new RequestDispatcher(sessions, requestLog)
                // Session
                .register(new PingHandler())
                .register(new LoginHandler(services.authentication(), sessions))
                .register(new LogoutHandler(services.authentication(), sessions))
                .register(new ChangePasswordHandler(services.authentication()))
                // Reports
                .register(new SubmitReportHandler(services.reports()))
                .register(new FindReportByReferenceHandler(services.reports()))
                .register(new FindAllReportsHandler(services.reports()))
                .register(new NearbyReportsHandler(services.reports()))
                // Assessment
                .register(new AssessHandler(services.assessments()))
                .register(new QueueByPriorityHandler(services.assessments()))
                .register(new AwaitingAssessmentHandler(services.assessments()))
                .register(new ResolveReportHandler(services.assessments()))
                // Coordination and departments
                .register(new AllocateHandler(services.coordination(),
                        services.departments()))
                .register(new UpdateStatusHandler(services.coordination()))
                .register(new TasksForDepartmentHandler(services.coordination()))
                .register(new ReportForTaskHandler(services.coordination()))
                .register(new AvailableDepartmentsHandler(services.coordination()))
                .register(new FindAllDepartmentsHandler(services.departments()))
                .register(new SetDepartmentAvailabilityHandler(services.departments()))
                // Alerts
                .register(new IssueAlertHandler(services.alerts()))
                .register(new FindAllAlertsHandler(services.alerts()))
                // Accounts
                .register(new CreateAccountHandler(services.accounts()))
                .register(new DeactivateAccountHandler(services.accounts()))
                .register(new ActivateAccountHandler(services.accounts()))
                .register(new AssignRoleHandler(services.accounts()))
                .register(new FindAllAccountsHandler(services.accounts()))
                // Audit
                .register(new FindAllAuditHandler(services.audit()))
                .register(new VerifyChainHandler(services.audit()))
                // Migration
                .register(new MigrateHandler(services.migration()));
    }

    /**
     * Says which declared operations have no handler.
     *
     * Printed at startup so that a forgotten registration is noticed then
     * rather than when a user presses the control that needs it. It is not a
     * reason to refuse to start: the protocol deliberately declares the
     * operations all three members of the team agreed, and the handlers arrive
     * as each feature is built.
     *
     * @param dispatcher the dispatcher the handlers were registered with
     */
    private static void reportUnhandled(RequestDispatcher dispatcher) {
        List<Operation> missing = dispatcher.unhandledOperations();
        if (missing.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Operation operation : missing) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(operation.wireName());
        }
        log("Declared with no handler yet, and refused if requested: " + names);
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
        StringBuilder names = new StringBuilder();
        for (SeedData.StartingAccount account : SeedData.startingAccounts()) {
            accountService.create(account.username(), account.plainPassword(),
                    account.fullName(), account.role(), account.departmentId(),
                    "system");
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(account.username());
        }
        log("Seeded the starting accounts: " + names + ".");
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
