package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.db.SchemaInitialiser;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.ReportStatus;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.StatusUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T182 to T196: storing and reading disaster reports in MySQL.
 *
 * <h2>These tests need a database, and skip without one</h2>
 * Every other test in this project runs anywhere. These cannot: a data access
 * class that has never spoken to a database has not been tested, only
 * described. Mocking the JDBC calls would check that this class calls the
 * methods it was written to call, which is not the same as checking that the
 * SQL is accepted or that a report survives the round trip.
 *
 * <p>So they run against a real database when one is reachable, and are
 * skipped when it is not. A skipped test is reported as skipped rather than
 * passing quietly, so the suite never claims to have checked something it did
 * not. The build then succeeds on a machine with no database, which matters
 * because the rest of the suite must stay runnable by anyone.
 *
 * <p>Connection settings come from db.properties and the usual overrides, so
 * these run against the same database the application uses. Each test clears
 * the tables it touches first, so one test cannot be affected by what another
 * left behind.
 */
@DisplayName("MySqlDisasterReportRepository")
class MySqlDisasterReportRepositoryTest {

    private static final String FIRE_DEPARTMENT_ID = "DEP-01";
    private static final String HOSPITAL_ID = "DEP-02";

    /**
     * The database these tests work in.
     *
     * Deliberately not the one the application uses. These tests empty the
     * tables they touch, and from the moment the Assessment 2 data has been
     * migrated, running them against the real database would destroy it. A
     * test suite that quietly deletes the user's data is worse than no test
     * suite, because nobody expects it and nobody checks for it.
     *
     * The schema is created here exactly as it is for the real database, by
     * the same initialiser, so the tests still run against the real schema.
     */
    private static final String TEST_DATABASE = "drs_enhanced_test";

    private ConnectionFactory connections;
    private MySqlDisasterReportRepository repository;
    private String originalDatabaseName;

    @BeforeEach
    void setUp() throws SQLException {
        originalDatabaseName = System.getProperty(DatabaseConfig.KEY_DB_NAME);
        System.setProperty(DatabaseConfig.KEY_DB_NAME, TEST_DATABASE);

        DatabaseConfig config = DatabaseConfig.load();
        ConnectionFactory candidate = new ConnectionFactory(config);

        Assumptions.assumeTrue(isReachable(candidate),
                "No MySQL database is reachable at " + config.describeTarget()
                        + ", so the database tests are skipped. Start MySQL and set "
                        + "DRS_DB_PASSWORD to run them.");

        this.connections = candidate;
        new SchemaInitialiser(connections).initialise();

        clearTables();
        seedDepartments();

        this.repository = new MySqlDisasterReportRepository(connections);
    }

    /**
     * Puts the database name back, whether or not the test ran.
     *
     * Leaving it pointing at the test database would send every later test in
     * the same JVM somewhere unexpected. That exact mistake, in a different
     * class, cost half a morning earlier in this project.
     */
    @AfterEach
    void restoreDatabaseName() {
        if (originalDatabaseName == null) {
            System.clearProperty(DatabaseConfig.KEY_DB_NAME);
        } else {
            System.setProperty(DatabaseConfig.KEY_DB_NAME, originalDatabaseName);
        }
    }

    private static boolean isReachable(ConnectionFactory connections) {
        try {
            connections.readServerVersion();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Empties the tables these tests write to.
     *
     * Children are deleted before parents even though the foreign keys cascade,
     * because being explicit about the order means this still works if a
     * cascade is ever changed.
     */
    private void clearTables() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM status_updates");
            statement.executeUpdate("DELETE FROM alerts");
            statement.executeUpdate("DELETE FROM response_tasks");
            statement.executeUpdate("DELETE FROM assessments");
            statement.executeUpdate("DELETE FROM disaster_reports");
            statement.executeUpdate("DELETE FROM reporters");
        }
    }

    private void seedDepartments() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT IGNORE INTO departments (department_id, name, contact_email) "
                    + "VALUES ('DEP-01', 'Fire and Emergency', 'dispatch@fire.gov.au'), "
                    + "('DEP-02', 'Hospital', 'emergency@health.gov.au')");
        }
    }

    // ---- builders ------------------------------------------------------

    private static DisasterReport submittedReport(String reference) {
        return new DisasterReport(reference, DisasterType.FIRE,
                new Location("12 Quay Street, Rockhampton", -23.377723, 150.510742),
                Severity.HIGH, "Smoke seen from the top floor.",
                new Reporter("RP-00000001", "A. Reporter", "0412345678"));
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T182: a submitted report is stored and read back unchanged")
    void submittedReportRoundTrips() {
        DisasterReport original = submittedReport("DR-0001");
        LocalDateTime reportedAt = original.getReportedAt();

        repository.save(original);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();

        assertEquals("DR-0001", read.getReferenceNumber());
        assertEquals(DisasterType.FIRE, read.getType());
        assertEquals("12 Quay Street, Rockhampton", read.getLocation().getAddress());
        assertEquals(Severity.HIGH, read.getReportedSeverity());
        assertEquals("Smoke seen from the top floor.", read.getDescription());
        assertEquals(ReportStatus.SUBMITTED, read.currentStatus());
        assertEquals(reportedAt.truncatedTo(ChronoUnit.MICROS), read.getReportedAt(),
                "The time of the report must survive to the microsecond.");
    }

    @Test
    @DisplayName("A timestamp is truncated to microseconds and loses nothing else")
    void timestampsAreStoredToMicrosecondPrecision() {
        // DATETIME(6) holds six decimal places, which is microseconds.
        // LocalDateTime.now() offers nanoseconds, and how many of them are
        // real depends on the operating system: Linux returns nanosecond
        // precision, Windows typically less. A report therefore comes back
        // with its time truncated, not altered, and the discarded digits are
        // below the resolution anything in this system distinguishes.
        //
        // This is tested rather than assumed because comparing a saved object
        // with a reloaded one is a natural thing to do, and the result would
        // otherwise look like data loss on one machine and be fine on another.
        DisasterReport original = submittedReport("DR-0001");
        repository.save(original);

        LocalDateTime read = repository.findByReference("DR-0001")
                .orElseThrow().getReportedAt();

        assertEquals(original.getReportedAt().truncatedTo(ChronoUnit.MICROS), read);
        assertEquals(0, read.getNano() % 1000,
                "Nothing finer than a microsecond can be stored.");
        assertTrue(Duration.between(read, original.getReportedAt()).abs()
                        .compareTo(Duration.ofMillis(1)) < 0,
                "The truncation must be under a millisecond, not a rounding of "
                        + "the whole value.");
    }

    @Test
    @DisplayName("T183: coordinates survive to six decimal places")
    void coordinatesKeepTheirPrecision() {
        DisasterReport original = submittedReport("DR-0001");
        repository.save(original);

        Location read = repository.findByReference("DR-0001").orElseThrow().getLocation();

        assertEquals(-23.377723, read.getLatitude(), 0.0000001,
                "Six decimal places is about 0.11 metres, which is the "
                        + "resolution the nearby-reports feature relies on.");
        assertEquals(150.510742, read.getLongitude(), 0.0000001);
    }

    @Test
    @DisplayName("T184: a report with no coordinates is stored with both columns null")
    void reportWithoutCoordinates() {
        DisasterReport original = new DisasterReport("DR-0002", DisasterType.FLOOD,
                new Location("Riverside Drive"), Severity.MEDIUM, null, null);

        repository.save(original);

        DisasterReport read = repository.findByReference("DR-0002").orElseThrow();
        assertNull(read.getLocation().getLatitude());
        assertNull(read.getLocation().getLongitude());
        assertFalse(read.getLocation().hasCoordinates());
    }

    @Test
    @DisplayName("T185: an anonymous report keeps no reporter")
    void anonymousReportHasNoReporter() {
        // Reporting is open to the public, so a report may carry no reporter
        // at all. The column is nullable for exactly this case.
        DisasterReport original = new DisasterReport("DR-0003", DisasterType.OTHER,
                new Location("Unknown"), Severity.LOW, null, null);

        repository.save(original);

        DisasterReport read = repository.findByReference("DR-0003").orElseThrow();
        assertTrue(read.isAnonymous());
        assertNull(read.getReporter());
    }

    @Test
    @DisplayName("T186: a named reporter is stored and read back")
    void namedReporterRoundTrips() {
        repository.save(submittedReport("DR-0001"));

        Reporter read = repository.findByReference("DR-0001").orElseThrow().getReporter();

        assertNotNull(read);
        assertEquals("RP-00000001", read.getReporterId());
        assertEquals("A. Reporter", read.getName());
        assertEquals("0412345678", read.getContactNumber());
    }

    @Test
    @DisplayName("T187: an assessment is stored with its report and moves it to ASSESSED")
    void assessmentRoundTrips() {
        DisasterReport report = submittedReport("DR-0001");
        Assessment assessment = new Assessment("AS-0001", Severity.SEVERE,
                "Spreading to the adjoining building.", "coordinator");
        report.attachAssessment(assessment);

        repository.save(report);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();
        assertEquals(ReportStatus.ASSESSED, read.currentStatus());
        assertTrue(read.isAssessed());
        assertEquals(Severity.SEVERE, read.getAssessment().getSeverity());
        assertEquals("Spreading to the adjoining building.",
                read.getAssessment().getNotes());
        assertEquals("coordinator", read.getAssessment().getAssessedBy());
        assertEquals(assessment.getAssessedAt().truncatedTo(ChronoUnit.MICROS),
                read.getAssessment().getAssessedAt());
    }

    @Test
    @DisplayName("T188: dispatching to two departments stores both tasks in one transaction")
    void twoTasksAreStoredTogether() {
        DisasterReport report = dispatchedReport("DR-0001");

        repository.save(report);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();
        assertEquals(ReportStatus.DISPATCHED, read.currentStatus());
        assertEquals(2, read.getTasks().size());
        assertEquals(FIRE_DEPARTMENT_ID, read.getTasks().get(0).getDepartmentId());
        assertEquals("Fire and Emergency", read.getTasks().get(0).getDepartmentName());
        assertEquals(HOSPITAL_ID, read.getTasks().get(1).getDepartmentId());
    }

    @Test
    @DisplayName("T189: whether a dispatch notice failed is remembered")
    void notifiedFlagRoundTrips() {
        // A notice that failed is the whole reason the coordinator is asked to
        // follow up by another route, so losing this would lose the feature.
        DisasterReport report = dispatchedReport("DR-0001");
        report.getTasks().get(0).setNotified(true);
        report.getTasks().get(1).setNotified(false);

        repository.save(report);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();
        assertTrue(read.getTasks().get(0).isNotified());
        assertFalse(read.getTasks().get(1).isNotified());
    }

    @Test
    @DisplayName("T190: a task's status history is stored and replayed in order")
    void statusHistoryRoundTrips() {
        DisasterReport report = dispatchedReport("DR-0001");
        ResponseTask task = report.getTasks().get(0);

        task.addUpdate(new StatusUpdate("SU-0001", ResponseStatus.IN_PROGRESS,
                "Crew on scene.", "fire.crew"));
        task.addUpdate(new StatusUpdate("SU-0002", ResponseStatus.COMPLETE,
                "Fire out.", "fire.crew"));

        repository.save(report);

        ResponseTask read = repository.findByReference("DR-0001")
                .orElseThrow().getTasks().get(0);

        assertEquals(2, read.getHistory().size());
        assertEquals(ResponseStatus.IN_PROGRESS, read.getHistory().get(0).getStatus());
        assertEquals("Crew on scene.", read.getHistory().get(0).getNote());
        assertEquals(ResponseStatus.COMPLETE, read.getHistory().get(1).getStatus());
        assertEquals(ResponseStatus.COMPLETE, read.getStatus(),
                "The task's status is restored by replaying its history.");
        assertTrue(read.isComplete());
    }

    @Test
    @DisplayName("T191: a resolved report is read back resolved")
    void resolvedReportRoundTrips() {
        DisasterReport report = dispatchedReport("DR-0001");
        for (ResponseTask task : report.getTasks()) {
            task.addUpdate(new StatusUpdate("SU-" + task.getTaskId(),
                    ResponseStatus.COMPLETE, "Done.", "fire.crew"));
        }
        report.resolve();

        repository.save(report);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();
        assertEquals(ReportStatus.RESOLVED, read.currentStatus());
        assertTrue(read.allTasksComplete());
    }

    @Test
    @DisplayName("T192: an alert issued for a report is stored with it")
    void alertRoundTrips() {
        DisasterReport report = submittedReport("DR-0001");
        Alert alert = new Alert("AL-0001", AlertType.EVACUATION,
                "Quay Street and surrounds", "Leave the area immediately.", "coordinator");
        alert.setDelivered(true);
        report.addAlert(alert);

        repository.save(report);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();
        assertEquals(1, read.getAlerts().size());
        assertEquals(AlertType.EVACUATION, read.getAlerts().get(0).getType());
        assertEquals("Leave the area immediately.", read.getAlerts().get(0).getMessage());
        assertTrue(read.getAlerts().get(0).isDelivered());
    }

    @Test
    @DisplayName("T193: saving the same report twice updates it rather than duplicating it")
    void savingTwiceUpdates() {
        // AlertService stores an alert through the report and again through
        // the alert repository, so storing the same thing twice has to be
        // safe rather than a second row.
        DisasterReport report = submittedReport("DR-0001");
        repository.save(report);

        report.attachAssessment(new Assessment("AS-0001", Severity.LOW, null, "coordinator"));
        repository.save(report);

        assertEquals(1, repository.count());
        assertEquals(ReportStatus.ASSESSED,
                repository.findByReference("DR-0001").orElseThrow().currentStatus());
    }

    @Test
    @DisplayName("T194: a second report under the same reference is refused, not silently merged")
    void duplicateReferenceIsRefused() {
        // The race this guards against: two clients both read the same count,
        // both generate DR-0001, and both try to store a different disaster
        // under it.
        //
        // insert is what a new report uses. save would have treated the second
        // one as a change to the first and overwritten it, and a reported
        // disaster would have disappeared with nothing recorded anywhere. That
        // is the whole reason the two are separate methods.
        repository.insert(submittedReport("DR-0001"));

        DisasterReport second = new DisasterReport("DR-0001", DisasterType.FLOOD,
                new Location("A different street entirely"), Severity.SEVERE,
                "A different disaster.", null);

        DuplicateReferenceException refused = assertThrows(DuplicateReferenceException.class,
                () -> repository.insert(second));
        assertEquals("DR-0001", refused.getReference());

        DisasterReport survivor = repository.findByReference("DR-0001").orElseThrow();
        assertEquals(DisasterType.FIRE, survivor.getType(),
                "The report stored first must be untouched.");
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("T195: every report is returned by findAll, each with its own children")
    void findAllReturnsEachReportWithItsOwnChildren() {
        repository.save(submittedReport("DR-0001"));
        repository.save(dispatchedReport("DR-0002"));
        repository.save(new DisasterReport("DR-0003", DisasterType.EARTHQUAKE,
                new Location("Civic Square"), Severity.LOW, null, null));

        List<DisasterReport> all = repository.findAll();

        assertEquals(3, all.size());
        assertEquals(3, repository.count());

        DisasterReport dispatched = all.stream()
                .filter(r -> r.getReferenceNumber().equals("DR-0002"))
                .findFirst().orElseThrow();
        assertEquals(2, dispatched.getTasks().size(),
                "Loading many reports at once must not attach one report's "
                        + "tasks to another.");

        DisasterReport plain = all.stream()
                .filter(r -> r.getReferenceNumber().equals("DR-0003"))
                .findFirst().orElseThrow();
        assertTrue(plain.getTasks().isEmpty());
        assertFalse(plain.isAssessed());
    }

    @Test
    @DisplayName("T196: a reference that is not stored returns an empty Optional")
    void unknownReferenceReturnsEmpty() {
        repository.save(submittedReport("DR-0001"));

        assertTrue(repository.findByReference("DR-9999").isEmpty());
        assertTrue(repository.findByReference(null).isEmpty());
        assertTrue(repository.findByReference("").isEmpty());
    }

    @Test
    @DisplayName("Rows that contradict themselves are reported rather than returned")
    void inconsistentRowsAreRefused() throws SQLException {
        // A report marked as dispatched with no response tasks describes
        // something the application cannot produce. Reading it back quietly
        // would hand the rest of the system a report whose status is wrong.
        repository.save(submittedReport("DR-0001"));

        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE disaster_reports SET status = 'DISPATCHED' "
                    + "WHERE reference_number = 'DR-0001'");
        }

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> repository.findByReference("DR-0001"));
        assertTrue(refused.getMessage().contains("inconsistent"),
                "The message must say the stored rows disagree: " + refused.getMessage());
    }

    @Test
    @DisplayName("Counting an empty table returns zero rather than failing")
    void countOnEmptyTable() {
        assertEquals(0, repository.count());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    @DisplayName("A null report is refused")
    void nullReportRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        assertThrows(IllegalArgumentException.class, () -> repository.insert(null));
    }

    @Test
    @DisplayName("save updates an existing report, where insert would refuse it")
    void saveUpdatesWhatInsertWouldRefuse() {
        // The two methods exist precisely because these are different
        // intentions, and one method could not tell them apart.
        repository.insert(submittedReport("DR-0001"));

        DisasterReport stored = repository.findByReference("DR-0001").orElseThrow();
        stored.attachAssessment(new Assessment("AS-0001", Severity.SEVERE,
                "Worse than first reported.", "coordinator"));

        repository.save(stored);

        DisasterReport read = repository.findByReference("DR-0001").orElseThrow();
        assertEquals(ReportStatus.ASSESSED, read.currentStatus());
        assertEquals(Severity.SEVERE, read.getAssessment().getSeverity());
        assertEquals(1, repository.count());

        assertThrows(DuplicateReferenceException.class,
                () -> repository.insert(stored),
                "insert must refuse a reference that is taken, even by this "
                        + "very report.");
    }

    @Test
    @DisplayName("A failure part way through leaves no trace of the report")
    void failedSaveWritesNothing() {
        // Dispatching work writes the report's status, a row per task and
        // their updates. A failure in the middle must not leave a report
        // marked as dispatched with only some of its tasks stored, which is
        // what the transaction is for.
        //
        // The failure is provoked by dispatching to a department that does not
        // exist, so the foreign key rejects the task after the report row has
        // already been written inside the transaction.
        DisasterReport report = submittedReport("DR-0001");
        report.attachAssessment(new Assessment("AS-0001", Severity.HIGH, null, "coordinator"));
        report.addTask(new ResponseTask("RT-0001",
                new Department("DEP-99", "A department that was never seeded", null)));

        assertThrows(IllegalStateException.class, () -> repository.insert(report));

        assertEquals(0, repository.count(),
                "The report row must have been rolled back with the task that "
                        + "failed, not left behind.");
        assertTrue(repository.findByReference("DR-0001").isEmpty());
    }

    /**
     * A report that has been assessed and dispatched to both departments.
     *
     * @param reference the reference to give it
     * @return the report
     */
    private static DisasterReport dispatchedReport(String reference) {
        DisasterReport report = submittedReport(reference);
        report.attachAssessment(new Assessment("AS-" + reference, Severity.SEVERE,
                "Serious.", "coordinator"));
        report.addTask(new ResponseTask("RT-" + reference + "-1",
                new Department(FIRE_DEPARTMENT_ID, "Fire and Emergency",
                        "dispatch@fire.gov.au")));
        report.addTask(new ResponseTask("RT-" + reference + "-2",
                new Department(HOSPITAL_ID, "Hospital", "emergency@health.gov.au")));
        return report;
    }
}
