package com.cqu.drs;

import com.cqu.drs.client.DrsClient;
import com.cqu.drs.client.RemoteAccountService;
import com.cqu.drs.client.RemoteAlertService;
import com.cqu.drs.client.RemoteAssessmentService;
import com.cqu.drs.client.RemoteAuditService;
import com.cqu.drs.client.RemoteAuthenticationService;
import com.cqu.drs.client.RemoteCoordinationService;
import com.cqu.drs.client.RemoteDepartmentService;
import com.cqu.drs.client.RemoteReportService;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.JsonAlertRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonDepartmentRepository;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import com.cqu.drs.repository.JsonUserRepository;
import com.cqu.drs.repository.UserRepository;
import com.cqu.drs.service.AccountOperations;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AlertOperations;
import com.cqu.drs.service.AlertService;
import com.cqu.drs.service.AssessmentOperations;
import com.cqu.drs.service.AssessmentService;
import com.cqu.drs.service.AuditOperations;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.AuthenticationOperations;
import com.cqu.drs.service.AuthenticationService;
import com.cqu.drs.service.CoordinationOperations;
import com.cqu.drs.service.CoordinationService;
import com.cqu.drs.service.DepartmentOperations;
import com.cqu.drs.service.DepartmentService;
import com.cqu.drs.service.ReportOperations;
import com.cqu.drs.service.ReportService;

import java.util.Optional;

/**
 * Builds the services once and hands them to whichever controller needs them,
 * so the wiring is done in one place rather than repeated in every screen.
 *
 * <h2>The one place that knows whether the application is in two tiers or
 * three</h2>
 * Every getter returns an interface. Behind each one is either the service
 * that reaches a repository in this process, which is how DRS-Initial worked,
 * or a service that sends a request to the server and waits for the reply.
 * Which it is depends on one setting, and nothing above this class can tell
 * the difference.
 *
 * <p>That is what keeps MVC intact across the split. A controller calls a
 * method on something that performs an operation on the model. Whether that
 * something happens to be on the other side of a socket is not a concern a
 * controller has, and so the presentation tier needed no rewriting when the
 * application became three-tier.
 *
 * <h2>Why local mode is kept</h2>
 * It is not dead weight. It is how the application starts when no server is
 * running, which the team needs in order to work on screens without one, and
 * it is the configuration Assessment 2 was marked in. Keeping both behind one
 * interface is also the demonstration that the interface was worth having: the
 * same screens run against either.
 *
 * <p>A single shared instance is used because this is a desktop client with
 * one person at the keyboard. The server does not use this class at all: it
 * constructs its own services, with the MySQL repositories, in
 * {@code ServerMain}.
 */
public final class AppContext {

    private static AppContext instance;

    private final AccountOperations accountService;
    private final AlertOperations alertService;
    private final AssessmentOperations assessmentService;
    private final AuditOperations auditService;
    private final AuthenticationOperations authenticationService;
    private final CoordinationOperations coordinationService;
    private final DepartmentOperations departmentService;
    private final ReportOperations reportService;
    private final DrsClient client;
    private final String clientMode;

    private AppContext() {
        DatabaseConfig config = DatabaseConfig.load();
        this.clientMode = config.clientMode();

        if (config.isRemoteClient()) {
            // Three tiers. The client holds no repository at all: every
            // question goes to the server, which owns the database.
            this.client = new DrsClient(config);
            this.accountService = new RemoteAccountService(client);
            this.alertService = new RemoteAlertService(client);
            this.assessmentService = new RemoteAssessmentService(client);
            this.auditService = new RemoteAuditService(client);
            this.authenticationService = new RemoteAuthenticationService(client);
            this.coordinationService = new RemoteCoordinationService(client);
            this.departmentService = new RemoteDepartmentService(client);
            this.reportService = new RemoteReportService(client);

        } else {
            // The DRS-Initial arrangement, kept as the default so that the
            // application starts whether or not a server is running.
            this.client = null;

            JsonDataStore store = new JsonDataStore();
            DisasterReportRepository reportRepository =
                    new JsonDisasterReportRepository(store);
            UserRepository userRepository = new JsonUserRepository(store);
            AlertRepository alertRepository = new JsonAlertRepository(store);
            AuditRepository auditRepository = new JsonAuditRepository(store);
            DepartmentRepository departmentRepository = new JsonDepartmentRepository(store);

            AuditService audit = new AuditService(auditRepository);
            AccountService accounts = new AccountService(userRepository, audit);

            this.auditService = audit;
            this.accountService = accounts;
            this.authenticationService = new AuthenticationService(userRepository, audit);
            this.assessmentService = new AssessmentService(reportRepository, audit);
            this.coordinationService = new CoordinationService(
                    reportRepository, departmentRepository, audit);
            this.alertService = new AlertService(reportRepository, alertRepository, audit);
            this.departmentService = new DepartmentService(departmentRepository, audit);
            this.reportService = new ReportService(reportRepository, audit);

            // Seeding belongs to whichever tier owns the data. In local mode
            // that is this process; in remote mode the server does it before
            // it opens its port, and a client that tried would be writing to
            // a store it does not own.
            departmentRepository.seedIfEmpty();
            seedAccountsIfNone(accounts);
        }
    }

    /**
     * The shared context, created on first use.
     *
     * @return the context
     */
    public static synchronized AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    /**
     * Creates the starting accounts when none exist, so that the application
     * can be signed into the first time it is run.
     *
     * @param accounts the service that creates accounts
     */
    private static void seedAccountsIfNone(AccountOperations accounts) {
        if (!accounts.findAll().isEmpty()) {
            return;
        }
        accounts.create("admin", "admin123", "System Administrator",
                UserRole.ADMINISTRATOR, null, "system");
        accounts.create("coordinator", "coord123", "Emergency Coordinator",
                UserRole.COORDINATOR, null, "system");
        accounts.create("fire.crew", "crew1234", "Fire and Emergency Crew",
                UserRole.RESPONDER, "DEP-01", "system");
    }

    public AccountOperations getAccountService() {
        return accountService;
    }

    public AlertOperations getAlertService() {
        return alertService;
    }

    public AssessmentOperations getAssessmentService() {
        return assessmentService;
    }

    /**
     * The audit trail, for reading and for checking it has not been altered.
     *
     * There is no method on it that writes an entry, and that is deliberate.
     * Entries are written by whichever tier carries out the work, as part of
     * carrying it out, so a screen cannot write one and a screen cannot forget
     * to.
     *
     * @return the audit operations
     */
    public AuditOperations getAuditService() {
        return auditService;
    }

    public AuthenticationOperations getAuthenticationService() {
        return authenticationService;
    }

    public CoordinationOperations getCoordinationService() {
        return coordinationService;
    }

    /**
     * The departments, and whether they can take work.
     *
     * This replaces the department repository the screens used to be handed.
     * A repository is a data tier concern and a client in three tiers has
     * none, so the screens ask for an operation to be performed instead of
     * reaching for storage.
     *
     * @return the department operations
     */
    public DepartmentOperations getDepartmentService() {
        return departmentService;
    }

    public ReportOperations getReportService() {
        return reportService;
    }

    /**
     * How this client reaches its data, for the window title and the log.
     *
     * @return "local" or "remote"
     */
    public String getClientMode() {
        return clientMode;
    }

    /**
     * The connection to the server, or null when running locally.
     *
     * @return the client, or null
     */
    public DrsClient getClient() {
        return client;
    }

    /**
     * Closes anything that must not be left open when the window closes.
     *
     * Without this the socket is left to the operating system to tidy up when
     * the process ends, and the server sees a connection that was never closed
     * rather than one that ended.
     */
    public void shutdown() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * The user currently signed in, if any.
     *
     * @return the current user, or an empty Optional
     */
    public Optional<User> getCurrentUser() {
        return authenticationService.getCurrentUser();
    }

    /**
     * The username to record against an action, or null when nobody is signed
     * in, which is the case for a report lodged by a member of the public.
     *
     * <p>In three tiers the server does not take the actor from this value: it
     * reads the username from the session the request arrived on. This remains
     * because the local services, which have no session, still need telling
     * who is acting.
     *
     * @return the current username, or null
     */
    public String getCurrentUsername() {
        return getCurrentUser().map(User::getUsername).orElse(null);
    }
}
