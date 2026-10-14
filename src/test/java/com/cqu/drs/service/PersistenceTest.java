package com.cqu.drs.service;

import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.StatusUpdate;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T76 to T80 and T87 to T90: reading data back after it has been
 * written, including an anonymous report, which is the case most likely to
 * break because the reporter is absent from the stored record.
 */
@DisplayName("Persistence round trips")
class PersistenceTest {

    private static final String ADDRESS = "12 Bridge Street, Melbourne";

    private JsonDataStore store;
    private DisasterReportRepository reportRepository;
    private ReportService reportService;
    private AuditService auditService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        store = new JsonDataStore(tempDir);
        reportRepository = new DisasterReportRepository(store);
        auditService = new AuditService(new AuditRepository(store));
        reportService = new ReportService(reportRepository, auditService);
    }

    @Test
    @DisplayName("T76: a report with no reporter is accepted and stored")
    void submitAcceptsAnonymousReport() {
        String reference = reportService.submitReport(DisasterType.FIRE, ADDRESS,
                null, null, Severity.HIGH, "Smoke seen", null);

        DisasterReport stored = reportService.findByReference(reference).orElseThrow();
        assertTrue(stored.isAnonymous());
        assertNull(stored.getReporter());
    }

    @Test
    @DisplayName("T78: an anonymous reporter finds their report by its reference")
    void anonymousTrackByReference() {
        String reference = reportService.submitReport(DisasterType.FLOOD, ADDRESS,
                null, null, Severity.MEDIUM, null, null);
        assertTrue(reportService.findByReference(reference).isPresent());
    }

    @Test
    @DisplayName("T79: a reference returns only that report, not a listing of others")
    void anonymousCannotListOtherReports() {
        String first = reportService.submitReport(DisasterType.FIRE, ADDRESS,
                null, null, Severity.HIGH, null, null);
        reportService.submitReport(DisasterType.FLOOD, "Elsewhere",
                null, null, Severity.LOW, null, null);

        DisasterReport found = reportService.findByReference(first).orElseThrow();
        assertEquals(first, found.getReferenceNumber());
    }

    @Test
    @DisplayName("T80: an anonymous report reloads with its reporter still absent")
    void anonymousReportSurvivesRoundTrip() {
        String reference = reportService.submitReport(DisasterType.FIRE, ADDRESS,
                null, null, Severity.HIGH, null, null);

        // A second repository reads the file afresh, so nothing is cached in memory.
        DisasterReportRepository reread = new DisasterReportRepository(store);
        DisasterReport reloaded = reread.findByReference(reference).orElseThrow();

        assertNull(reloaded.getReporter());
        assertTrue(reloaded.isAnonymous());
        assertEquals(DisasterType.FIRE, reloaded.getType());
    }

    @Test
    @DisplayName("T87: a stored report reloads field for field")
    void reportSurvivesRoundTrip() {
        Reporter reporter = new Reporter("RP-01", "J. Perera", "0412345678");
        String reference = reportService.submitReport(DisasterType.EARTHQUAKE, ADDRESS,
                -37.8136, 144.9631, Severity.SEVERE, "Building shook", reporter);

        DisasterReport reloaded = new DisasterReportRepository(store)
                .findByReference(reference).orElseThrow();

        assertEquals(DisasterType.EARTHQUAKE, reloaded.getType());
        assertEquals(ADDRESS, reloaded.getLocation().getAddress());
        assertTrue(reloaded.getLocation().hasCoordinates());
        assertEquals(Severity.SEVERE, reloaded.getReportedSeverity());
        assertEquals("Building shook", reloaded.getDescription());
        assertEquals("J. Perera", reloaded.getReporter().getName());
        assertNotNull(reloaded.getReportedAt(), "The timestamp must survive the round trip.");
    }

    @Test
    @DisplayName("T88: a missing data file yields an empty list rather than an error")
    void loadEmptyWhenFileAbsent(@TempDir Path emptyDir) {
        DisasterReportRepository empty =
                new DisasterReportRepository(new JsonDataStore(emptyDir));
        assertTrue(empty.findAll().isEmpty());
        assertEquals(0, empty.count());
    }

    @Test
    @DisplayName("T89: audit entries are kept in the order they were made")
    void auditKeepsEntriesInOrder() {
        auditService.record("admin", "First action");
        auditService.record("admin", "Second action");
        auditService.record("admin", "Third action");

        assertEquals(3, auditService.findAll().size());
        assertEquals("First action", auditService.findAll().get(0).getAction());
        assertEquals("Third action", auditService.findAll().get(2).getAction());
    }

    @Test
    @DisplayName("T90: an assessment, its tasks and their history all reload intact")
    void nestedObjectsSurviveRoundTrip() {
        DisasterReport report = new DisasterReport("DR-0001", DisasterType.FIRE,
                new Location(ADDRESS), Severity.HIGH, "Smoke", null);
        report.attachAssessment(new Assessment(UUID.randomUUID().toString(),
                Severity.SEVERE, "Confirmed on site", "coordinator"));

        ResponseTask task = new ResponseTask("RT-1",
                new Department("DEP-01", "Fire and Emergency", "a@b.c"));
        report.addTask(task);
        task.addUpdate(new StatusUpdate(UUID.randomUUID().toString(),
                ResponseStatus.IN_PROGRESS, "Crew on site", "crew"));
        reportRepository.save(report);

        DisasterReport reloaded = new DisasterReportRepository(store)
                .findByReference("DR-0001").orElseThrow();

        assertTrue(reloaded.isAssessed());
        assertEquals(Severity.SEVERE, reloaded.getAssessment().getSeverity());
        assertEquals(1, reloaded.getTasks().size());
        assertEquals("Fire and Emergency", reloaded.getTasks().get(0).getDepartmentName());
        assertEquals(1, reloaded.getTasks().get(0).getHistory().size());
        assertEquals(ResponseStatus.IN_PROGRESS,
                reloaded.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("A staff account reloads as the right kind of user")
    void userSubclassSurvivesRoundTrip() {
        UserRepository users = new UserRepository(store);
        AccountService accounts = new AccountService(users, auditService);
        accounts.create("crew", "Str0ngPass", "Fire Crew",
                UserRole.RESPONDER, "DEP-01", "setup");

        User reloaded = new UserRepository(store).findByUsername("crew").orElseThrow();
        assertEquals(UserRole.RESPONDER, reloaded.getRole());
        assertTrue(reloaded.authenticate("Str0ngPass"),
                "The stored password digest must still match after reloading.");
    }

    @Test
    @DisplayName("Saving the same report twice does not duplicate it")
    void saveReplacesRatherThanDuplicates() {
        String reference = reportService.submitReport(DisasterType.FIRE, ADDRESS,
                null, null, Severity.HIGH, null, null);
        DisasterReport stored = reportRepository.findByReference(reference).orElseThrow();
        reportRepository.save(stored);

        assertEquals(1, reportRepository.count());
    }

    @Test
    @DisplayName("The eight departments are seeded once and not again")
    void departmentsSeededOnce() {
        DepartmentRepository departments = new DepartmentRepository(store);
        assertTrue(departments.seedIfEmpty());
        assertEquals(8, departments.findAll().size());
        assertFalse(departments.seedIfEmpty(), "Seeding must not run a second time.");
        assertEquals(8, departments.findAll().size());
    }
}
