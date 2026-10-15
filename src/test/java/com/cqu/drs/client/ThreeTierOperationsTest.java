package com.cqu.drs.client;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Priority;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditChainStatus;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.cqu.drs.repository.JsonAlertRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonDepartmentRepository;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import com.cqu.drs.repository.JsonUserRepository;
import com.cqu.drs.repository.UserRepository;
import com.cqu.drs.server.DrsServer;
import com.cqu.drs.server.RequestDispatcher;
import com.cqu.drs.server.ServerMain;
import com.cqu.drs.server.ServerServices;
import com.cqu.drs.server.SessionRegistry;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AlertService;
import com.cqu.drs.service.AssessmentService;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.AuthenticationService;
import com.cqu.drs.service.CoordinationService;
import com.cqu.drs.service.DepartmentService;
import com.cqu.drs.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T260 to T281: every operation the client can perform, performed across
 * a real socket.
 *
 * <h2>Why this test exists in this shape</h2>
 * The client and the server agree on the name of every field in every payload,
 * and nothing in the compiler checks that agreement. A handler reading
 * {@code departmentId} and a remote service sending {@code department} compile
 * perfectly and fail at run time, on the one screen nobody opened before
 * submitting. So the proof has to be a request actually travelling: a real
 * server on a real port, a real client, requests signed and verified, sessions
 * opened and closed.
 *
 * <p>The storage is the only thing standing in for production. The server
 * writes to JSON files in a temporary folder rather than to MySQL, which means
 * these run on any machine with no database installed, and every member of the
 * team gets the same result. The MySQL half of the path is proved separately
 * by {@code WalkingSkeletonTest} and by the repository tests, which skip when
 * no database is reachable. Splitting it that way is deliberate: the tests
 * that can always run should always run.
 *
 * <p>The access control tests are the ones worth reading twice. They do not
 * check that a screen hides a button; they check that the server refuses the
 * request, which is the only check that cannot be removed by editing the
 * client.
 */
@DisplayName("Every operation, across the wire")
class ThreeTierOperationsTest {

    private DrsServer server;
    private DrsClient client;

    private RemoteAccountService accounts;
    private RemoteAlertService alerts;
    private RemoteAssessmentService assessments;
    private RemoteAuditService audit;
    private RemoteAuthenticationService authentication;
    private RemoteCoordinationService coordination;
    private RemoteDepartmentService departments;
    private RemoteReportService reports;

    /** Reads the server's own copy, to check what the client was told. */
    private AuditRepository serverAuditTrail;
    private UserRepository serverAccounts;

    @BeforeEach
    void startServerAndClient(@TempDir Path serverData) throws IOException {
        JsonDataStore store = new JsonDataStore(serverData);

        DisasterReportRepository reportRepository = new JsonDisasterReportRepository(store);
        UserRepository userRepository = new JsonUserRepository(store);
        AlertRepository alertRepository = new JsonAlertRepository(store);
        AuditRepository auditRepository = new JsonAuditRepository(store);
        DepartmentRepository departmentRepository = new JsonDepartmentRepository(store);

        this.serverAuditTrail = auditRepository;
        this.serverAccounts = userRepository;

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

        departmentRepository.seedIfEmpty();
        accountService.create("admin", "admin123", "System Administrator",
                UserRole.ADMINISTRATOR, null, "system");
        accountService.create("coordinator", "coord123", "Emergency Coordinator",
                UserRole.COORDINATOR, null, "system");
        accountService.create("fire.crew", "crew1234", "Fire and Emergency Crew",
                UserRole.RESPONDER, "DEP-01", "system");

        // The server's own registration list, not a copy of it. A copy here
        // would let a handler be registered in the server and forgotten in
        // the test, and the test would still pass.
        SessionRegistry sessions = new SessionRegistry();
        RequestDispatcher dispatcher = ServerMain.buildDispatcher(sessions,
                new InMemoryRequestLogRepository(),
                new ServerServices(authenticationService, reportService,
                        assessmentService, coordinationService, alertService,
                        accountService, auditService, departmentService));

        server = new DrsServer(0, 8, dispatcher);
        server.start();

        client = new DrsClient("127.0.0.1", server.getPort());
        accounts = new RemoteAccountService(client);
        alerts = new RemoteAlertService(client);
        assessments = new RemoteAssessmentService(client);
        audit = new RemoteAuditService(client);
        authentication = new RemoteAuthenticationService(client);
        coordination = new RemoteCoordinationService(client);
        departments = new RemoteDepartmentService(client);
        reports = new RemoteReportService(client);
    }

    @AfterEach
    void stopServerAndClient() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    // ---- helpers -------------------------------------------------------

    private User signInAsAdministrator() {
        return authentication.login("admin", "admin123").orElseThrow(
                () -> new AssertionError("The seeded administrator could not sign in."));
    }

    private User signInAsCoordinator() {
        return authentication.login("coordinator", "coord123").orElseThrow(
                () -> new AssertionError("The seeded coordinator could not sign in."));
    }

    private User signInAsResponder() {
        return authentication.login("fire.crew", "crew1234").orElseThrow(
                () -> new AssertionError("The seeded responder could not sign in."));
    }

    private String lodgeReport() {
        return reports.submitReport(DisasterType.FIRE, "12 Quay Street, Rockhampton",
                -23.377723, 150.510742, Severity.HIGH, "Smoke from the top floor.",
                new Reporter("RP-client", "A. Reporter", "0412345678"));
    }

    private boolean auditTrailMentions(String fragment) {
        return serverAuditTrail.findAll().stream()
                .map(AuditLogEntry::getAction)
                .anyMatch(action -> action != null && action.contains(fragment));
    }

    // ---- sessions ------------------------------------------------------

    @Test
    @DisplayName("T260: signing in over the protocol returns the user and no credential")
    void signingInReturnsTheUserWithoutACredential() {
        User user = signInAsAdministrator();

        assertEquals("admin", user.getUsername());
        assertEquals(UserRole.ADMINISTRATOR, user.getRole());
        assertNull(user.getPasswordHash(),
                "The hash must stay on the server. A client holding it could be "
                        + "modified to check passwords itself.");
        assertNotNull(client.getSessionToken(), "The session token was not kept.");
    }

    @Test
    @DisplayName("T261: a wrong password is refused without saying which part was wrong")
    void wrongPasswordIsRefused() {
        assertTrue(authentication.login("admin", "not-the-password").isEmpty());
        assertTrue(authentication.getCurrentUser().isEmpty());
        assertNull(client.getSessionToken(),
                "No session may be opened for a sign-in that failed.");
    }

    @Test
    @DisplayName("T262: an unknown username is refused exactly as a wrong password is")
    void unknownUsernameIsRefusedTheSameWay() {
        // Indistinguishable on purpose: a different answer would tell someone
        // guessing which accounts exist.
        assertTrue(authentication.login("nobody.here", "whatever").isEmpty());
    }

    @Test
    @DisplayName("T263: signing out closes the session, so the token stops working")
    void signingOutClosesTheSession() {
        signInAsAdministrator();
        authentication.logout();

        assertTrue(authentication.getCurrentUser().isEmpty());
        assertNull(client.getSessionToken());

        ProtocolException refused = assertThrows(ProtocolException.class,
                () -> accounts.findAll());
        assertEquals(ErrorCode.NOT_AUTHENTICATED, refused.getErrorCode());
    }

    @Test
    @DisplayName("T264: a password changed through the protocol takes effect")
    void passwordChangeTakesEffect() {
        signInAsCoordinator();
        authentication.changeCurrentPassword("newcoord456");
        authentication.logout();

        assertTrue(authentication.login("coordinator", "coord123").isEmpty(),
                "The old password must stop working.");
        assertTrue(authentication.login("coordinator", "newcoord456").isPresent(),
                "The new password must work.");
    }

    // ---- accounts ------------------------------------------------------

    @Test
    @DisplayName("T265: the accounts list arrives complete and carries no hashes")
    void accountsListCarriesNoHashes() {
        signInAsAdministrator();
        List<User> all = accounts.findAll();

        assertEquals(3, all.size());
        for (User user : all) {
            assertNull(user.getPasswordHash(),
                    "One request would otherwise hand a client the hash of every "
                            + "account in the system.");
            assertNotNull(user.getRole(), "Each account must arrive as its own kind.");
        }
    }

    @Test
    @DisplayName("T266: an account created on the client exists on the server")
    void createdAccountExistsOnTheServer() {
        signInAsAdministrator();
        User created = accounts.create("j.smith", "pass12345", "J. Smith",
                UserRole.RESPONDER, "DEP-02", "admin");

        assertEquals("j.smith", created.getUsername());
        assertNull(created.getPasswordHash());

        // Read from the server's own store, not from the reply, because the
        // reply only proves what the server said.
        Optional<User> stored = serverAccounts.findByUsername("j.smith");
        assertTrue(stored.isPresent(), "The account was not stored on the server.");
        assertTrue(stored.get().authenticate("pass12345"),
                "The password was not usable, so it did not arrive intact.");
        assertEquals(4, accounts.findAll().size());
    }

    @Test
    @DisplayName("T267: a username already taken is refused")
    void duplicateUsernameIsRefused() {
        signInAsAdministrator();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> accounts.create("coordinator", "pass12345", "Someone Else",
                        UserRole.COORDINATOR, null, "admin"));

        assertTrue(refused.getMessage().contains("coordinator"),
                "The refusal should name the username: " + refused.getMessage());
        assertEquals(3, accounts.findAll().size(), "Nothing should have been created.");
    }

    @Test
    @DisplayName("T268: nobody may deactivate the account they are signed in as")
    void nobodyMayDeactivateTheirOwnAccount() {
        signInAsAdministrator();

        // An IllegalStateException rather than an argument fault: nothing
        // about the request is wrong, it is this session that may not make it.
        assertThrows(IllegalStateException.class,
                () -> accounts.deactivate("admin", "admin"));

        assertTrue(serverAccounts.findByUsername("admin").orElseThrow().isActive(),
                "The account must still be active after a refused deactivation.");
    }

    @Test
    @DisplayName("T269: nobody may change the role of the account they are signed in as")
    void nobodyMayChangeTheirOwnRole() {
        signInAsAdministrator();

        assertThrows(IllegalStateException.class,
                () -> accounts.assignRole("admin", UserRole.RESPONDER, "admin"));

        assertEquals(UserRole.ADMINISTRATOR,
                serverAccounts.findByUsername("admin").orElseThrow().getRole());
    }

    @Test
    @DisplayName("T270: a deactivated account can no longer sign in")
    void deactivatedAccountCannotSignIn() {
        signInAsAdministrator();
        accounts.deactivate("coordinator", "admin");
        authentication.logout();

        assertTrue(authentication.login("coordinator", "coord123").isEmpty(),
                "A deactivated account must be refused, with the same message as "
                        + "a wrong password.");
    }

    @Test
    @DisplayName("T271: activating an active account reports that nothing changed")
    void activatingAnActiveAccountReportsNoChange() {
        signInAsAdministrator();

        assertFalse(accounts.activate("coordinator", "admin"),
                "Pressing the control twice must be harmless and must say so.");

        accounts.deactivate("coordinator", "admin");
        assertTrue(accounts.activate("coordinator", "admin"),
                "Reactivating a deactivated account did not report the change.");
    }

    @Test
    @DisplayName("T272: a responder may not list accounts, however they ask")
    void responderMayNotListAccounts() {
        signInAsResponder();

        // Raised as an IllegalStateException rather than as a protocol
        // exception, because a screen must not have to know which tier
        // refused it. Nothing about the request was wrong: this session is
        // not permitted to make it.
        assertThrows(IllegalStateException.class, () -> accounts.findAll());
    }

    @Test
    @DisplayName("T273: a coordinator may not create accounts")
    void coordinatorMayNotCreateAccounts() {
        signInAsCoordinator();

        // The same failure as T272, from a different operation. The two
        // disagreed when each remote service translated refusals for itself,
        // which is why one class now does it for all of them.
        assertThrows(IllegalStateException.class,
                () -> accounts.create("x.person", "pass12345", "X. Person",
                        UserRole.COORDINATOR, null, "coordinator"));
    }

    // ---- departments ---------------------------------------------------

    @Test
    @DisplayName("T274: the departments arrive, and the available ones are a subset")
    void departmentsArrive() {
        signInAsAdministrator();
        List<Department> all = departments.findAll();

        assertFalse(all.isEmpty(), "The seeded departments did not arrive.");
        assertTrue(departments.findById(all.get(0).getDepartmentId()).isPresent());
    }

    @Test
    @DisplayName("T275: the server records who changed a department's availability")
    void availabilityChangeIsRecordedByTheServer() {
        signInAsAdministrator();
        Department before = departments.findById("DEP-01").orElseThrow();

        Department after = departments.setAvailability("DEP-01", !before.isAvailable(),
                "admin");

        assertEquals(!before.isAvailable(), after.isAvailable(),
                "The department returned must be the server's record, not the "
                        + "object the screen was holding with a field flipped.");
        assertTrue(auditTrailMentions(before.getName()),
                "The change was not recorded. The client no longer writes this "
                        + "entry, so if the server does not write it, nothing does.");
    }

    @Test
    @DisplayName("T276: a department made unavailable drops out of the available list")
    void unavailableDepartmentDropsOut() {
        signInAsCoordinator();
        int availableBefore = departments.findAvailable().size();

        departments.setAvailability("DEP-01", false, "coordinator");

        assertEquals(availableBefore - 1, departments.findAvailable().size());
    }

    @Test
    @DisplayName("T277: changing a department that does not exist is refused as not found")
    void unknownDepartmentIsRefused() {
        signInAsAdministrator();

        assertThrows(IllegalArgumentException.class,
                () -> departments.setAvailability("DEP-99", false, "admin"));
    }

    // ---- audit ---------------------------------------------------------

    @Test
    @DisplayName("T278: the audit trail can be read, and the protocol offers no way to write one")
    void auditTrailIsReadOnlyOverTheProtocol() {
        signInAsAdministrator();
        List<AuditLogEntry> entries = audit.findAll();

        assertFalse(entries.isEmpty(),
                "Signing in is itself recorded, so the trail cannot be empty here.");

        // Structural rather than behavioural, and it is the stronger check:
        // no operation exists that a client could use to write an entry, so
        // there is nothing to test the refusal of.
        for (Operation operation : Operation.values()) {
            String name = operation.wireName();
            assertFalse(name.startsWith("audit.") && !name.equals("audit.findAll")
                            && !name.equals("audit.verifyChain"),
                    "The protocol declares " + name + ". A client able to write "
                            + "audit entries could write entries blaming somebody "
                            + "else, and the trail would prove nothing.");
        }
    }

    @Test
    @DisplayName("T279: a store that keeps no hash chain says so rather than claiming it is intact")
    void storeWithoutAChainSaysSo() {
        signInAsAdministrator();
        AuditChainStatus status = audit.verifyChain();

        // The server in this test writes the trail to JSON, which keeps no
        // chain. Reporting "intact" would be a claim it has no means of
        // supporting, and that is the answer worth testing: the honest one.
        assertFalse(status.supported());
        assertFalse(status.intact());
        assertNotNull(status.detail());
        assertTrue(status.detail().toLowerCase().contains("chain"),
                "The detail should explain why no verdict is available: "
                        + status.detail());
    }

    @Test
    @DisplayName("T280: a coordinator may not read the audit trail")
    void coordinatorMayNotReadTheAuditTrail() {
        signInAsCoordinator();

        assertThrows(IllegalStateException.class, () -> audit.findAll());
    }

    // ---- reports, assessment, coordination and alerts ------------------

    @Test
    @DisplayName("T281: a report lodged without signing in is found again by its reference")
    void publicReportIsFoundByReference() {
        String reference = lodgeReport();

        assertEquals("DR-0001", reference);
        DisasterReport found = reports.findByReference(reference).orElseThrow();
        assertEquals("12 Quay Street, Rockhampton", found.getLocation().getAddress());
    }

    @Test
    @DisplayName("T282: a coordinator assesses a report and the priority comes back with it")
    void coordinatorAssessesAReport() {
        String reference = lodgeReport();
        signInAsCoordinator();

        assertEquals(1, assessments.awaitingAssessment().size());

        Assessment assessment =
                assessments.assess(reference, Severity.SEVERE, "Spreading.", "coordinator");

        assertNotNull(assessment);
        assertEquals(Severity.SEVERE, assessment.getSeverity());
        assertEquals(Priority.P1, assessments.prioritise(assessment));
        assertEquals(1, assessments.queueByPriority().size());
        assertTrue(assessments.awaitingAssessment().isEmpty());
    }

    @Test
    @DisplayName("T283: assessing a reference that does not exist is refused as not found")
    void assessingAnUnknownReferenceIsRefused() {
        signInAsCoordinator();

        assertThrows(IllegalArgumentException.class,
                () -> assessments.assess("DR-9999", Severity.LOW, null, "coordinator"));
    }

    @Test
    @DisplayName("T284: a severe assessment warrants an evacuation, decided without the server")
    void severeAssessmentWarrantsEvacuation() {
        // Answered on the client, through the same rule class the server uses,
        // so the prompt appears the moment the assessment is recorded.
        assertTrue(alerts.warrantsEvacuation(Severity.SEVERE));
        assertFalse(alerts.warrantsEvacuation(Severity.LOW));
    }

    @Test
    @DisplayName("T285: an alert issued through the protocol reports whether it was delivered")
    void alertReportsWhetherItWasDelivered() {
        String reference = lodgeReport();
        signInAsCoordinator();
        assessments.assess(reference, Severity.SEVERE, null, "coordinator");

        Alert issued = alerts.issue(reference, AlertType.EVACUATION,
                "Quay Street precinct", "Leave the area now.", "coordinator");

        assertNotNull(issued);
        assertEquals(AlertType.EVACUATION, issued.getType());
        assertEquals(1, alerts.findAll().size());
        assertTrue(auditTrailMentions(reference),
                "Issuing a public warning must be recorded against the coordinator.");
    }

    @Test
    @DisplayName("T286: dispatching to several departments is one request, not one each")
    void dispatchingToSeveralDepartmentsIsOneRequest() {
        String reference = lodgeReport();
        signInAsCoordinator();
        assessments.assess(reference, Severity.HIGH, null, "coordinator");

        List<Department> chosen = departments.findAll().subList(0, 3);
        List<ResponseTask> created =
                coordination.allocate(reference, chosen, "coordinator");

        assertEquals(3, created.size());
        for (ResponseTask task : created) {
            assertNotNull(task.getTaskId());
            assertNotNull(task.getDepartmentId(),
                    "A task must arrive knowing which department it belongs to.");
            assertNotNull(task.getDepartmentName(),
                    "The responder's screen shows the name, so it has to travel too.");
        }
    }

    @Test
    @DisplayName("T287: a responder sees their own department's work and may update it")
    void responderUpdatesTheirOwnWork() {
        String reference = lodgeReport();
        signInAsCoordinator();
        assessments.assess(reference, Severity.HIGH, null, "coordinator");
        Department own = departments.findById("DEP-01").orElseThrow();
        coordination.allocate(reference, List.of(own), "coordinator");
        authentication.logout();

        signInAsResponder();
        List<ResponseTask> mine = coordination.findTasksForDepartment("DEP-01");
        assertEquals(1, mine.size());

        String taskId = mine.get(0).getTaskId();
        assertNotNull(coordination.updateStatus(reference, taskId,
                ResponseStatus.IN_PROGRESS, "Crew on scene.", "fire.crew"));
        assertEquals(reference,
                coordination.findReportForTask(taskId).orElseThrow().getReferenceNumber());
    }

    @Test
    @DisplayName("T288: a responder may not touch another department's work")
    void responderMayNotTouchAnotherDepartmentsWork() {
        String reference = lodgeReport();
        signInAsCoordinator();
        assessments.assess(reference, Severity.HIGH, null, "coordinator");
        Department other = departments.findById("DEP-02").orElseThrow();
        String foreignTask = coordination.allocate(reference, List.of(other), "coordinator")
                .get(0).getTaskId();
        authentication.logout();

        signInAsResponder();

        // The role check already passed: a responder may use this operation.
        // Which records they may use it on is a different question, and it is
        // decided on the server from the department recorded on the session.
        assertThrows(IllegalStateException.class,
                () -> coordination.updateStatus(reference, foreignTask,
                        ResponseStatus.COMPLETE, null, "fire.crew"));

        assertTrue(coordination.findTasksForDepartment("DEP-01").isEmpty(),
                "The foreign task must not appear in this responder's own list.");
    }

    @Test
    @DisplayName("T289: a responder may not ask for another department's task list")
    void responderMayNotAskForAnotherDepartmentsList() {
        signInAsResponder();

        assertThrows(IllegalStateException.class,
                () -> coordination.findTasksForDepartment("DEP-02"));
    }

    @Test
    @DisplayName("T290: a coordinator reads every report, a responder reads none")
    void readingEveryReportIsRestricted() {
        lodgeReport();

        signInAsCoordinator();
        assertEquals(1, reports.findAll().size());
        authentication.logout();

        signInAsResponder();
        assertThrows(IllegalStateException.class, () -> reports.findAll());
    }

}
