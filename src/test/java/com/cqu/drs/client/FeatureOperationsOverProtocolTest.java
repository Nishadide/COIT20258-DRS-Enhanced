package com.cqu.drs.client;

import com.cqu.drs.migration.DataMigrationService;
import com.cqu.drs.migration.JsonMigrationSource;
import com.cqu.drs.migration.MigrationLedger;
import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.cqu.drs.repository.JsonAlertRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonCasualtyRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonDepartmentRepository;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import com.cqu.drs.repository.JsonResourceRepository;
import com.cqu.drs.repository.JsonUserRepository;
import com.cqu.drs.security.FieldCipher;
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
import com.cqu.drs.service.CasualtyService;
import com.cqu.drs.service.CoordinationService;
import com.cqu.drs.service.DepartmentService;
import com.cqu.drs.service.ReportService;
import com.cqu.drs.service.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T356 to T368: the two new features, across a real socket.
 *
 * <h2>What is being proved here rather than elsewhere</h2>
 * The repository tests prove that allocation is safe under contention and that
 * casualty records are encrypted at rest. Those are properties of the data
 * tier. What they cannot prove is that the features are reachable through the
 * protocol, that the payload field names agree between the client and the
 * server, and that the access rules are enforced on the server rather than by
 * a screen hiding a button.
 *
 * <p>The access rules are the ones worth reading. An administrator may ask how
 * many casualties there are and may not ask who they are, and that is checked
 * here by asking as an administrator and being refused.
 */
@DisplayName("The new features, across the wire")
class FeatureOperationsOverProtocolTest {

    private DrsServer server;
    private DrsClient client;

    private RemoteAuthenticationService authentication;
    private RemoteResourceService resources;
    private RemoteCasualtyService casualties;
    private RemoteReportService reports;
    private RemoteAssessmentService assessments;

    private String reference;

    @BeforeEach
    void startServerAndClient(@TempDir Path serverData) throws IOException {
        JsonDataStore store = new JsonDataStore(serverData);

        JsonDisasterReportRepository reportRepository =
                new JsonDisasterReportRepository(store);
        JsonUserRepository userRepository = new JsonUserRepository(store);
        JsonAlertRepository alertRepository = new JsonAlertRepository(store);
        JsonAuditRepository auditRepository = new JsonAuditRepository(store);
        JsonDepartmentRepository departmentRepository =
                new JsonDepartmentRepository(store);
        JsonResourceRepository resourceRepository = new JsonResourceRepository(store);
        JsonCasualtyRepository casualtyRepository =
                new JsonCasualtyRepository(store, new FieldCipher(FieldCipher.newKey()));

        AuditService audit = new AuditService(auditRepository);
        AccountService accounts = new AccountService(userRepository, audit);

        departmentRepository.seedIfEmpty();
        resourceRepository.seedIfEmpty();
        accounts.create("admin", "admin123", "System Administrator",
                UserRole.ADMINISTRATOR, null, "system");
        accounts.create("coordinator", "coord123", "Emergency Coordinator",
                UserRole.COORDINATOR, null, "system");
        accounts.create("fire.crew", "crew1234", "Fire and Emergency Crew",
                UserRole.RESPONDER, "DEP-01", "system");

        SessionRegistry sessions = new SessionRegistry();
        RequestDispatcher dispatcher = ServerMain.buildDispatcher(sessions,
                new InMemoryRequestLogRepository(),
                new ServerServices(
                        new AuthenticationService(userRepository, audit),
                        new ReportService(reportRepository, audit),
                        new AssessmentService(reportRepository, audit),
                        new CoordinationService(reportRepository, departmentRepository,
                                audit),
                        new AlertService(reportRepository, alertRepository, audit),
                        accounts,
                        audit,
                        new DepartmentService(departmentRepository, audit),
                        new ResourceService(resourceRepository, audit),
                        new CasualtyService(casualtyRepository, reportRepository, audit),
                        new DataMigrationService(new JsonMigrationSource(serverData),
                                departmentRepository, userRepository, reportRepository,
                                alertRepository, auditRepository, new DoneLedger())));

        server = new DrsServer(0, 8, dispatcher);
        server.start();

        client = new DrsClient("127.0.0.1", server.getPort());
        authentication = new RemoteAuthenticationService(client);
        resources = new RemoteResourceService(client);
        casualties = new RemoteCasualtyService(client);
        reports = new RemoteReportService(client);
        assessments = new RemoteAssessmentService(client);

        // One assessed disaster for the features to work against.
        reference = reports.submitReport(DisasterType.EARTHQUAKE, "Quay Street",
                null, null, Severity.HIGH, "Building partially collapsed.", null);
        signInAsCoordinator();
        assessments.assess(reference, Severity.SEVERE, null, "coordinator");
        authentication.logout();
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

    private void signInAsCoordinator() {
        authentication.login("coordinator", "coord123").orElseThrow(
                () -> new AssertionError("The coordinator could not sign in."));
    }

    private void signInAsResponder() {
        authentication.login("fire.crew", "crew1234").orElseThrow(
                () -> new AssertionError("The responder could not sign in."));
    }

    private void signInAsAdministrator() {
        authentication.login("admin", "admin123").orElseThrow(
                () -> new AssertionError("The administrator could not sign in."));
    }

    private Resource firstPool() {
        return resources.findAll().stream()
                .filter(resource -> resource.getAvailableUnits() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No resources were seeded."));
    }

    // ---- resource allocation -------------------------------------------

    @Test
    @DisplayName("T356: a coordinator allocates resources across the wire")
    void coordinatorAllocates() {
        signInAsCoordinator();
        Resource pool = firstPool();
        int before = pool.getAvailableUnits();

        ResourceAllocation allocation =
                resources.allocate(pool.getResourceId(), reference, 1, "coordinator");

        assertNotNull(allocation.getAllocationId());
        assertEquals(1, allocation.getUnits());
        assertTrue(allocation.isHeld());
        assertNotNull(allocation.getResourceName(),
                "The allocation must arrive knowing which pool it came from.");

        Resource after = resources.findAll().stream()
                .filter(resource -> resource.getResourceId().equals(pool.getResourceId()))
                .findFirst().orElseThrow();
        assertEquals(before - 1, after.getAvailableUnits());
    }

    @Test
    @DisplayName("T357: allocating more than the pool holds is refused with what is left")
    void overAllocationIsRefused() {
        signInAsCoordinator();
        Resource pool = firstPool();

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> resources.allocate(pool.getResourceId(), reference,
                        pool.getTotalUnits() + 5, "coordinator"));

        assertTrue(refused.getMessage().contains(String.valueOf(pool.getAvailableUnits())),
                "The refusal should say how many are actually available: "
                        + refused.getMessage());
    }

    @Test
    @DisplayName("T358: the committed units show against the disaster and can be released")
    void allocationsAreListedAndReleased() {
        signInAsCoordinator();
        Resource pool = firstPool();

        ResourceAllocation allocation =
                resources.allocate(pool.getResourceId(), reference, 1, "coordinator");

        List<ResourceAllocation> forReport =
                resources.findAllocationsForReport(reference);
        assertEquals(1, forReport.size());
        assertEquals(allocation.getAllocationId(), forReport.get(0).getAllocationId());
        assertEquals(1, resources.findHeldAllocations().size());

        ResourceAllocation released =
                resources.release(allocation.getAllocationId(), "coordinator");
        assertFalse(released.isHeld());

        // Still listed after release: the record of what was committed
        // survives the units going back.
        assertEquals(1, resources.findAllocationsForReport(reference).size());
        assertTrue(resources.findHeldAllocations().isEmpty());
    }

    @Test
    @DisplayName("T359: releasing the same allocation twice is refused")
    void doubleReleaseIsRefused() {
        signInAsCoordinator();
        Resource pool = firstPool();
        ResourceAllocation allocation =
                resources.allocate(pool.getResourceId(), reference, 1, "coordinator");
        resources.release(allocation.getAllocationId(), "coordinator");

        assertThrows(RuntimeException.class,
                () -> resources.release(allocation.getAllocationId(), "coordinator"));
    }

    @Test
    @DisplayName("T360: a responder may not allocate resources")
    void responderMayNotAllocate() {
        signInAsCoordinator();
        String poolId = firstPool().getResourceId();
        authentication.logout();

        signInAsResponder();
        assertThrows(IllegalStateException.class,
                () -> resources.allocate(poolId, reference, 1, "fire.crew"));
    }

    @Test
    @DisplayName("T361: a responder sees their own department's pools and not another's")
    void responderIsConfinedToTheirOwnDepartment() {
        signInAsResponder();

        List<Resource> own = resources.findByDepartment("DEP-01");
        assertFalse(own.isEmpty(), "The responder's own department has pools.");
        for (Resource resource : own) {
            assertEquals("DEP-01", resource.getDepartmentId());
        }

        // The role check says a responder may use this operation. Which
        // department they may ask about is decided from the session.
        assertThrows(IllegalStateException.class,
                () -> resources.findByDepartment("DEP-02"));
    }

    // ---- casualty registry ---------------------------------------------

    @Test
    @DisplayName("T362: a responder registers a casualty and a coordinator sees it")
    void responderRegistersCasualty() {
        signInAsResponder();
        Casualty registered = casualties.register(reference, "Priya Raman", 34,
                "0412345678", TriageCategory.IMMEDIATE, "North stairwell",
                "Compound fracture.", "fire.crew");

        assertNotNull(registered.getCasualtyId());
        assertEquals("Priya Raman", registered.getName());
        assertEquals(TriageCategory.IMMEDIATE, registered.getTriage());
        authentication.logout();

        signInAsCoordinator();
        List<Casualty> forReport = casualties.findForReport(reference);
        assertEquals(1, forReport.size());
        assertEquals("Priya Raman", forReport.get(0).getName(),
                "The record is encrypted in the store and plain on the wire.");
    }

    @Test
    @DisplayName("T363: a casualty with no name is recorded rather than refused")
    void unidentifiedCasualtyIsRecorded() {
        signInAsResponder();

        Casualty registered = casualties.register(reference, null, null, null,
                TriageCategory.DELAYED, "Car park", null, "fire.crew");

        assertNull(registered.getName());
        assertTrue(registered.getDisplayName().startsWith("Unidentified"));
    }

    @Test
    @DisplayName("T364: retriage and referral are recorded against the right person")
    void retriageAndReferral() {
        signInAsResponder();
        Casualty registered = casualties.register(reference, "Sam Okafor", 50, null,
                TriageCategory.MINOR, "Foyer", null, "fire.crew");

        Casualty retriaged = casualties.retriage(registered.getCasualtyId(),
                TriageCategory.IMMEDIATE, "fire.crew");
        assertEquals(TriageCategory.IMMEDIATE, retriaged.getTriage());
        assertEquals("fire.crew", retriaged.getLastUpdatedBy());

        Casualty referred = casualties.refer(registered.getCasualtyId(),
                "Rockhampton Base Hospital", "fire.crew");
        assertEquals("Rockhampton Base Hospital", referred.getReferredTo());
        assertNotNull(referred.getReferredAt());
    }

    @Test
    @DisplayName("T365: a casualty recorded as deceased is not referred to a hospital")
    void deceasedCasualtyIsNotReferred() {
        signInAsResponder();
        Casualty registered = casualties.register(reference, null, null, null,
                TriageCategory.DECEASED, "Basement", null, "fire.crew");

        assertThrows(IllegalArgumentException.class,
                () -> casualties.refer(registered.getCasualtyId(), "Any Hospital",
                        "fire.crew"));
    }

    @Test
    @DisplayName("T366: an administrator may count casualties and may not read them")
    void administratorSeesCountsOnly() {
        signInAsResponder();
        casualties.register(reference, "Priya Raman", 34, "0412345678",
                TriageCategory.IMMEDIATE, "North stairwell", "Fracture.", "fire.crew");
        casualties.register(reference, "Sam Okafor", 50, null,
                TriageCategory.MINOR, "Foyer", null, "fire.crew");
        authentication.logout();

        signInAsAdministrator();

        Map<TriageCategory, Integer> summary = casualties.summaryFor(reference);
        assertEquals(1, summary.get(TriageCategory.IMMEDIATE));
        assertEquals(1, summary.get(TriageCategory.MINOR));
        assertEquals(0, summary.get(TriageCategory.DECEASED),
                "Every category is reported, including the empty ones.");

        // The counts tell an administrator how large the response is. The
        // records would tell them who was hurt, which is not theirs to know,
        // and the server refuses rather than the screen hiding a button.
        assertThrows(IllegalStateException.class,
                () -> casualties.findForReport(reference));
    }

    @Test
    @DisplayName("T367: an administrator may not register a casualty either")
    void administratorMayNotRegister() {
        signInAsAdministrator();

        assertThrows(IllegalStateException.class,
                () -> casualties.register(reference, "Someone", 20, null,
                        TriageCategory.MINOR, null, null, "admin"));
    }

    @Test
    @DisplayName("T368: casualties are listed most urgent first")
    void listIsOrderedByUrgency() {
        signInAsResponder();
        casualties.register(reference, "Minor", null, null, TriageCategory.MINOR,
                null, null, "fire.crew");
        casualties.register(reference, "Immediate", null, null,
                TriageCategory.IMMEDIATE, null, null, "fire.crew");
        casualties.register(reference, "Delayed", null, null, TriageCategory.DELAYED,
                null, null, "fire.crew");

        List<Casualty> ordered = casualties.findForReport(reference);

        assertEquals(TriageCategory.IMMEDIATE, ordered.get(0).getTriage());
        assertEquals(TriageCategory.DELAYED, ordered.get(1).getTriage());
        assertEquals(TriageCategory.MINOR, ordered.get(2).getTriage());
    }

    /** A ledger saying the migration is done; this test never migrates. */
    private static final class DoneLedger implements MigrationLedger {

        private final Map<String, String> entries = new HashMap<>();

        @Override
        public boolean hasRun(String item) {
            return true;
        }

        @Override
        public Optional<String> detailOf(String item) {
            return Optional.of("Migration is switched off for this test.");
        }

        @Override
        public void record(String item, String detail) {
            entries.put(item, detail);
        }
    }
}
