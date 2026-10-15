package com.cqu.drs.client;

import com.cqu.drs.migration.DataMigrationService;
import com.cqu.drs.migration.JsonMigrationSource;
import com.cqu.drs.migration.MigrationLedger;
import com.cqu.drs.migration.MigrationOutcome;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AlertRepository;
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
import com.cqu.drs.server.SessionRegistry;
import com.cqu.drs.server.handler.LoginHandler;
import com.cqu.drs.server.handler.LogoutHandler;
import com.cqu.drs.server.handler.MigrateHandler;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.AuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T307 to T310: the migration asked for across the wire.
 *
 * The migration itself is tested in {@code DataMigrationServiceTest}. What is
 * tested here is the part the specification actually asks for, which is that
 * the data is loaded into the database <em>through the server</em>: a client
 * holding no database connection and no files signs in, sends one request, and
 * is told what was moved.
 *
 * <p>The access control is the other half. A migration is the single most
 * destructive operation the system offers, so it matters that the refusal for
 * anyone who is not an administrator happens on the server, from the role
 * recorded against the session, where no client can reach it.
 */
@DisplayName("Migration through the server")
class MigrationOverProtocolTest {

    private DrsServer server;
    private DrsClient client;
    private RemoteAuthenticationService authentication;
    private RemoteMigrationService migration;

    private DisasterReportRepository targetReports;
    private AuditRepository targetAudit;

    @BeforeEach
    void startServerAndClient(@TempDir Path sourceFolder, @TempDir Path targetFolder)
            throws IOException {
        writeOneAssessment2Report(sourceFolder);

        JsonDataStore targetStore = new JsonDataStore(targetFolder);
        DepartmentRepository departments = new JsonDepartmentRepository(targetStore);
        UserRepository accounts = new JsonUserRepository(targetStore);
        AlertRepository alerts = new JsonAlertRepository(targetStore);
        targetReports = new JsonDisasterReportRepository(targetStore);
        targetAudit = new JsonAuditRepository(targetStore);

        AuditService auditService = new AuditService(targetAudit);
        AccountService accountService = new AccountService(accounts, auditService);
        AuthenticationService authenticationService =
                new AuthenticationService(accounts, auditService);

        departments.seedIfEmpty();
        accountService.create("admin", "admin123", "System Administrator",
                UserRole.ADMINISTRATOR, null, "system");
        accountService.create("coordinator", "coord123", "Emergency Coordinator",
                UserRole.COORDINATOR, null, "system");

        DataMigrationService migrationService = new DataMigrationService(
                new JsonMigrationSource(sourceFolder), departments, accounts,
                targetReports, alerts, targetAudit, new MapLedger());

        SessionRegistry sessions = new SessionRegistry();
        RequestDispatcher dispatcher =
                new RequestDispatcher(sessions, new InMemoryRequestLogRepository())
                        .register(new LoginHandler(authenticationService, sessions))
                        .register(new LogoutHandler(authenticationService, sessions))
                        .register(new MigrateHandler(migrationService));

        server = new DrsServer(0, 4, dispatcher);
        server.start();

        client = new DrsClient("127.0.0.1", server.getPort());
        authentication = new RemoteAuthenticationService(client);
        migration = new RemoteMigrationService(client);
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

    private static void writeOneAssessment2Report(Path folder) {
        JsonDisasterReportRepository reports =
                new JsonDisasterReportRepository(new JsonDataStore(folder));
        DisasterReport report = new DisasterReport("DR-0001", DisasterType.FLOOD,
                new Location("Riverside Drive, Rockhampton"), Severity.MEDIUM,
                "Water over the road.", null);
        report.setReportedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        reports.save(report);
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T307: an administrator migrates the data without touching the database")
    void administratorMigratesThroughTheServer() {
        authentication.login("admin", "admin123").orElseThrow();

        MigrationOutcome outcome = migration.migrate("admin");

        assertTrue(outcome.performed(), outcome.detail());
        assertEquals(1, outcome.reports());
        assertEquals(0, outcome.departments(),
                "This source folder holds no departments.json, and the migration "
                        + "reads what is there rather than seeding the source "
                        + "folder it is reading from.");

        // The client was told a count; this is the server's own store, which
        // is what the count was supposed to describe.
        assertEquals(1, targetReports.count());
        assertEquals("Riverside Drive, Rockhampton", targetReports
                .findByReference("DR-0001").orElseThrow().getLocation().getAddress());
    }

    @Test
    @DisplayName("T308: the migration is recorded against the administrator who asked")
    void migrationIsRecordedAgainstTheSession() {
        authentication.login("admin", "admin123").orElseThrow();
        migration.migrate("someone.else");

        // "someone.else" was passed by the caller and ignored. The server
        // records the username on the session, so a client cannot attribute a
        // migration to another person.
        boolean recordedAgainstAdmin = targetAudit.findAll().stream()
                .filter(entry -> entry.getAction().contains("Migrated"))
                .anyMatch(entry -> "admin".equals(entry.getActor()));

        assertTrue(recordedAgainstAdmin,
                "The migration must be recorded against the signed-in "
                        + "administrator, not against a name the client supplied.");
    }

    @Test
    @DisplayName("T309: a coordinator may not migrate, and nothing moves")
    void coordinatorMayNotMigrate() {
        authentication.login("coordinator", "coord123").orElseThrow();

        assertThrows(IllegalStateException.class, () -> migration.migrate("coordinator"));
        assertEquals(0, targetReports.count(),
                "A refused migration must move nothing at all.");
    }

    @Test
    @DisplayName("T310: migrating without signing in is refused")
    void signedOutClientMayNotMigrate() {
        // No session at all. The operation is not public, so the dispatcher
        // refuses it before any handler sees it.
        assertThrows(RuntimeException.class, () -> migration.migrate("nobody"));
        assertEquals(0, targetReports.count());
    }

    @Test
    @DisplayName("T311: asking twice through the server reports that it was already done")
    void askingTwiceThroughTheServerIsHarmless() {
        authentication.login("admin", "admin123").orElseThrow();

        MigrationOutcome first = migration.migrate("admin");
        MigrationOutcome second = migration.migrate("admin");

        assertTrue(first.performed());
        assertFalse(second.performed());
        assertTrue(second.detail().contains("already"), second.detail());
        assertEquals(1, targetReports.count(), "Nothing may be written twice.");
    }

    /** A ledger in a map, which is all this test needs. */
    private static final class MapLedger implements MigrationLedger {

        private final Map<String, String> entries = new HashMap<>();

        @Override
        public boolean hasRun(String item) {
            return entries.containsKey(item);
        }

        @Override
        public Optional<String> detailOf(String item) {
            return Optional.ofNullable(entries.get(item));
        }

        @Override
        public void record(String item, String detail) {
            entries.put(item, detail);
        }
    }
}
