package com.cqu.drs.migration;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.SystemAdministrator;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T291 to T300: the Assessment 2 data moving into the Assessment 3
 * store.
 *
 * <h2>Why these need no database</h2>
 * The migration is written against the repository interfaces on both sides, so
 * the destination here is a second set of JSON repositories in a second
 * temporary folder. What is being tested is the migration's own decisions:
 * what it moves, in what order, what it refuses, and what it does on the
 * second run. None of those are properties of MySQL.
 *
 * <p>What MySQL adds is the constraints, the transactions and the hash chain,
 * and those are tested where they live, by the repository tests that skip when
 * no database is reachable. A migration test that needed a database would be a
 * migration test that most of the team could not run.
 *
 * <p>The destination is set up the way a real one is: the eight departments
 * and the three starting accounts already present, because the server seeds
 * them before anybody can sign in to ask for a migration. That is what makes
 * the username clash in T292 and T293 a real case rather than an invented one.
 */
@DisplayName("Migrating the Assessment 2 data")
class DataMigrationServiceTest {

    private static final String ASSESSMENT_2_PASSWORD = "oldpass123";

    private MigrationSource source;

    private DepartmentRepository targetDepartments;
    private UserRepository targetAccounts;
    private DisasterReportRepository targetReports;
    private AlertRepository targetAlerts;
    private AuditRepository targetAudit;

    private InMemoryMigrationLedger ledger;
    private DataMigrationService migration;

    @BeforeEach
    void buildBothSides(@TempDir Path sourceFolder, @TempDir Path targetFolder) {
        writeAssessment2Data(sourceFolder);
        source = new JsonMigrationSource(sourceFolder);

        JsonDataStore targetStore = new JsonDataStore(targetFolder);
        targetDepartments = new JsonDepartmentRepository(targetStore);
        targetAccounts = new JsonUserRepository(targetStore);
        targetReports = new JsonDisasterReportRepository(targetStore);
        targetAlerts = new JsonAlertRepository(targetStore);
        targetAudit = new JsonAuditRepository(targetStore);

        // What a freshly started server looks like: reference data seeded so
        // that somebody can sign in, and no reports.
        targetDepartments.seedIfEmpty();
        targetAccounts.save(new SystemAdministrator("seeded-admin-id", "admin",
                "admin123", "System Administrator"));

        ledger = new InMemoryMigrationLedger();
        migration = new DataMigrationService(source, targetDepartments, targetAccounts,
                targetReports, targetAlerts, targetAudit, ledger);
    }

    /**
     * Writes a small but complete set of Assessment 2 data: two reports, one
     * of them assessed, an alert, two accounts and three audit entries written
     * out of order so that the ordering can be tested.
     *
     * @param folder the folder to write into
     */
    private static void writeAssessment2Data(Path folder) {
        JsonDataStore store = new JsonDataStore(folder);

        JsonDepartmentRepository departments = new JsonDepartmentRepository(store);
        departments.seedIfEmpty();
        Department unavailable = departments.findById("DEP-03").orElseThrow();
        unavailable.setAvailable(false);
        departments.save(unavailable);

        JsonUserRepository accounts = new JsonUserRepository(store);
        // The same username the destination has already seeded, with a
        // different internal identifier and a different password. This is the
        // clash a real migration meets every time.
        accounts.save(new SystemAdministrator("a2-admin-id", "admin",
                ASSESSMENT_2_PASSWORD, "A. Administrator"));
        accounts.save(new DepartmentResponder("a2-crew-id", "ses.crew",
                ASSESSMENT_2_PASSWORD, "S. Crew", "DEP-02"));

        JsonDisasterReportRepository reports = new JsonDisasterReportRepository(store);

        DisasterReport older = new DisasterReport("DR-0001", DisasterType.FLOOD,
                new Location("Riverside Drive, Rockhampton", -23.38, 150.51),
                Severity.MEDIUM, "Water over the road.",
                new Reporter("RP-0001", "A. Reporter", "0412345678"));
        older.setReportedAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        reports.save(older);

        DisasterReport newer = new DisasterReport("DR-0002", DisasterType.FIRE,
                new Location("12 Quay Street, Rockhampton"),
                Severity.HIGH, "Smoke from the top floor.", null);
        newer.setReportedAt(LocalDateTime.of(2026, 4, 2, 14, 30));
        newer.attachAssessment(new Assessment("as-1", Severity.SEVERE,
                "Spreading quickly.", "coordinator"));
        newer.addAlert(new Alert("AL-0001", AlertType.EVACUATION,
                "Quay Street precinct", "Leave the area now.", "coordinator"));
        reports.save(newer);

        JsonAuditRepository auditTrail = new JsonAuditRepository(store);
        AuditLogEntry middle = new AuditLogEntry("ae-2", "coordinator", "Assessed DR-0002");
        middle.setTimestamp(LocalDateTime.of(2026, 4, 2, 15, 0));
        AuditLogEntry first = new AuditLogEntry("ae-1", "anonymous", "Lodged DR-0001");
        first.setTimestamp(LocalDateTime.of(2026, 3, 1, 9, 0));
        AuditLogEntry last = new AuditLogEntry("ae-3", "coordinator", "Issued Evacuation");
        last.setTimestamp(LocalDateTime.of(2026, 4, 2, 15, 30));

        // Appended out of order on purpose.
        auditTrail.append(middle);
        auditTrail.append(last);
        auditTrail.append(first);
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T291: every report arrives, with what was hanging off it")
    void reportsArriveComplete() {
        MigrationOutcome outcome = migration.migrate("admin");

        assertTrue(outcome.performed());
        assertEquals(2, outcome.reports());
        assertEquals(2, targetReports.count());

        DisasterReport flood = targetReports.findByReference("DR-0001").orElseThrow();
        assertEquals(DisasterType.FLOOD, flood.getType());
        assertEquals("Riverside Drive, Rockhampton", flood.getLocation().getAddress());
        assertNotNull(flood.getReporter(), "The reporter was left behind.");
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), flood.getReportedAt(),
                "The original time must be carried over, not replaced by today.");

        DisasterReport fire = targetReports.findByReference("DR-0002").orElseThrow();
        assertTrue(fire.isAssessed(), "The assessment was left behind.");
        assertEquals(Severity.SEVERE, fire.getAssessment().getSeverity());
        assertEquals(1, fire.getAlerts().size(), "The alert was left behind.");
    }

    @Test
    @DisplayName("T292: an Assessment 2 password still works after the migration")
    void passwordsSurviveTheMigration() {
        migration.migrate("admin");

        User admin = targetAccounts.findByUsername("admin").orElseThrow();

        // The point of moving the stored hash rather than creating the account
        // again: creating it would hash a new password and nobody could sign
        // in with the one they had.
        assertTrue(admin.authenticate(ASSESSMENT_2_PASSWORD),
                "The password carried over from Assessment 2 does not work.");
        assertFalse(admin.authenticate("admin123"),
                "The seeded password should have been replaced by the migrated one.");
        assertEquals("A. Administrator", admin.getFullName());
    }

    @Test
    @DisplayName("T293: a username already present is updated, not duplicated")
    void clashingUsernameIsUpdatedRatherThanDuplicated() {
        migration.migrate("admin");

        List<User> all = targetAccounts.findAll();
        long admins = all.stream().filter(u -> "admin".equals(u.getUsername())).count();

        assertEquals(1, admins,
                "A second row with the same username would be refused by the "
                        + "unique key in MySQL, so the migration must not create one.");
        assertEquals(2, all.size(), "The migrated responder should also be there.");

        User admin = targetAccounts.findByUsername("admin").orElseThrow();
        assertEquals("seeded-admin-id", admin.getUserId(),
                "The row already there should have been updated, keeping its "
                        + "own surrogate identifier.");

        User crew = targetAccounts.findByUsername("ses.crew").orElseThrow();
        assertEquals(UserRole.RESPONDER, crew.getRole());
        assertTrue(crew instanceof DepartmentResponder);
        assertEquals("DEP-02", ((DepartmentResponder) crew).getDepartmentId());
    }

    @Test
    @DisplayName("T294: the availability a department had is carried over")
    void departmentAvailabilityIsCarriedOver() {
        migration.migrate("admin");

        Department moved = targetDepartments.findById("DEP-03").orElseThrow();
        assertFalse(moved.isAvailable(),
                "A department that was unavailable in Assessment 2 must still be "
                        + "unavailable, or the seeded value has silently won.");
    }

    @Test
    @DisplayName("T295: the audit entries arrive oldest first, keeping their actors")
    void auditEntriesArriveInOrder() {
        migration.migrate("admin");

        List<AuditLogEntry> entries = targetAudit.findAll();

        // Three migrated entries and one recording the migration itself.
        assertEquals(4, entries.size());
        assertEquals("Lodged DR-0001", entries.get(0).getAction());
        assertEquals("Assessed DR-0002", entries.get(1).getAction());
        assertEquals("Issued Evacuation", entries.get(2).getAction());

        assertEquals("anonymous", entries.get(0).getActor(),
                "A migrated entry keeps the actor it had, not the name of "
                        + "whoever ran the migration.");
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), entries.get(0).getTimestamp(),
                "A migrated entry keeps the time it had.");
    }

    @Test
    @DisplayName("T296: the migration itself is the last thing recorded in the trail")
    void migrationIsRecordedInTheTrail() {
        migration.migrate("admin");

        List<AuditLogEntry> entries = targetAudit.findAll();
        AuditLogEntry latest = entries.get(entries.size() - 1);

        assertEquals("admin", latest.getActor(),
                "The migration must be recorded against the administrator who "
                        + "asked for it.");
        assertTrue(latest.getAction().contains("Migrated"),
                "Unexpected entry: " + latest.getAction());
        assertNotNull(latest.getEntryId(),
                "The entry identifier is NOT NULL in the database, so an entry "
                        + "without one would be refused there and nowhere else.");
    }

    @Test
    @DisplayName("T297: asking twice moves nothing the second time and says why")
    void askingTwiceMovesNothing() {
        MigrationOutcome first = migration.migrate("admin");
        assertTrue(first.performed());
        int reportsAfterFirst = targetReports.count();
        int entriesAfterFirst = targetAudit.findAll().size();

        MigrationOutcome second = migration.migrate("admin");

        assertFalse(second.performed(), "The second run must do nothing.");
        assertEquals(0, second.total());
        assertTrue(second.detail().contains("already"),
                "The answer should say it has already been done: " + second.detail());

        assertEquals(reportsAfterFirst, targetReports.count(),
                "Nothing may be written on the second run.");
        assertEquals(entriesAfterFirst, targetAudit.findAll().size(),
                "Not even an audit entry, which would otherwise grow the trail "
                        + "every time somebody pressed the button.");
    }

    @Test
    @DisplayName("T298: the ledger records what was moved, not merely that something was")
    void ledgerRecordsWhatWasMoved() {
        MigrationOutcome outcome = migration.migrate("admin");

        String recorded = ledger.detailOf(DataMigrationService.LEDGER_ITEM).orElseThrow();

        assertTrue(recorded.contains(String.valueOf(outcome.total())), recorded);
        assertTrue(recorded.contains("admin"),
                "Who asked for it belongs in the record: " + recorded);
    }

    @Test
    @DisplayName("T299: migrating into a store that already holds reports is refused")
    void migratingOverLiveDataIsRefused() {
        // A report lodged after the server started, with the reference the
        // migration is about to bring a different report for.
        DisasterReport lodgedToday = new DisasterReport("DR-0001", DisasterType.EARTHQUAKE,
                new Location("Somewhere else entirely"), Severity.LOW,
                "Reported this morning.", null);
        targetReports.save(lodgedToday);

        IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> migration.migrate("admin"));

        assertTrue(refused.getMessage().contains("DR-0001")
                        || refused.getMessage().contains("already holds"),
                refused.getMessage());

        DisasterReport stillThere = targetReports.findByReference("DR-0001").orElseThrow();
        assertEquals(DisasterType.EARTHQUAKE, stillThere.getType(),
                "The live report must not have been replaced by the migrated one. "
                        + "Both systems number from DR-0001, so this is the case "
                        + "that would quietly overwrite today's work.");
        assertFalse(ledger.hasRun(DataMigrationService.LEDGER_ITEM),
                "A refused migration must not be recorded as done.");
    }

    @Test
    @DisplayName("T300: an empty source migrates nothing and does not fail")
    void emptySourceIsNotAFailure() {
        MigrationSource empty = new JsonMigrationSource(Path.of("no-such-folder"));
        DataMigrationService fromNothing = new DataMigrationService(empty,
                targetDepartments, targetAccounts, targetReports, targetAlerts,
                targetAudit, new InMemoryMigrationLedger());

        MigrationOutcome outcome = fromNothing.migrate("admin");

        assertTrue(outcome.performed());
        assertEquals(0, outcome.reports());
        assertEquals(0, outcome.accounts());
        assertEquals(0, targetReports.count());
    }

    @Test
    @DisplayName("T301: the source is not altered by migrating from it")
    void sourceIsLeftAlone() {
        List<DisasterReport> before = source.reports();
        int departmentsBefore = source.departments().size();
        int auditBefore = source.auditEntries().size();

        migration.migrate("admin");

        assertEquals(before.size(), source.reports().size());
        assertEquals(departmentsBefore, source.departments().size());
        assertEquals(auditBefore, source.auditEntries().size(),
                "The migration must not write its own entry into the trail it "
                        + "is reading. The Assessment 2 data is a submitted "
                        + "artefact and has to come out of this unchanged.");

        Optional<Department> stillUnavailable = source.departments().stream()
                .filter(d -> "DEP-03".equals(d.getDepartmentId()))
                .findFirst();
        assertTrue(stillUnavailable.isPresent());
        assertFalse(stillUnavailable.get().isAvailable());
    }
}
