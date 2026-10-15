package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.Severity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T245 to T251: public alerts in MySQL.
 *
 * One alert is written by two different repositories, because AlertService
 * stores it through the report that owns it and again through the alert store.
 * Most of these tests exist to prove the two writers do not undo each other,
 * which is the kind of fault that would show up as an alert quietly losing the
 * disaster it belongs to while still appearing in the list of all alerts.
 */
@DisplayName("MySqlAlertRepository")
class MySqlAlertRepositoryTest {

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlAlertRepository repository;
    private MySqlDisasterReportRepository reports;

    @BeforeEach
    void setUp() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();
        DatabaseTestSupport.clearAll(connections);
        repository = new MySqlAlertRepository(connections);
        reports = new MySqlDisasterReportRepository(connections);
    }

    @AfterEach
    void tearDown() {
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    private static Alert evacuation(String id) {
        return new Alert(id, AlertType.EVACUATION, "Quay Street and surrounds",
                "Leave the area immediately.", "coordinator");
    }

    private DisasterReport reportWith(Alert alert) {
        DisasterReport report = new DisasterReport("DR-0001", DisasterType.FIRE,
                new Location("12 Quay Street"), Severity.HIGH, null, null);
        report.addAlert(alert);
        return report;
    }

    private String storedReferenceFor(String alertId) throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT reference_number FROM alerts WHERE alert_id = '"
                     + alertId + "'")) {
            return results.next() ? results.getString(1) : null;
        }
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T245: an alert is stored and read back whole")
    void alertRoundTrips() {
        Alert original = evacuation("AL-0001");
        repository.save(original);

        List<Alert> all = repository.findAll();

        assertEquals(1, all.size());
        Alert read = all.get(0);
        assertEquals("AL-0001", read.getAlertId());
        assertEquals(AlertType.EVACUATION, read.getType());
        assertEquals("Quay Street and surrounds", read.getAffectedArea());
        assertEquals("Leave the area immediately.", read.getMessage());
        assertEquals("coordinator", read.getIssuedBy());
        assertEquals(original.getIssuedAt().truncatedTo(ChronoUnit.MICROS),
                read.getIssuedAt());
    }

    @Test
    @DisplayName("T246: whether an alert reached its recipients is remembered")
    void deliveredFlagRoundTrips() {
        // An alert that failed to broadcast is the whole reason a coordinator
        // is told to follow up another way, so losing this would lose the
        // behaviour.
        Alert delivered = evacuation("AL-0001");
        delivered.setDelivered(true);
        Alert failed = new Alert("AL-0002", AlertType.WARNING, "Riverside",
                "Flooding expected.", "coordinator");

        repository.save(delivered);
        repository.save(failed);

        List<Alert> all = repository.findAll();
        assertTrue(all.stream().filter(a -> a.getAlertId().equals("AL-0001"))
                .findFirst().orElseThrow().isDelivered());
        assertFalse(all.stream().filter(a -> a.getAlertId().equals("AL-0002"))
                .findFirst().orElseThrow().isDelivered());
    }

    @Test
    @DisplayName("T247: saving the same alert twice updates it rather than duplicating it")
    void savingTwiceUpdates() {
        Alert alert = evacuation("AL-0001");
        repository.save(alert);

        alert.setDelivered(true);
        repository.save(alert);

        assertEquals(1, repository.findAll().size());
        assertTrue(repository.findAll().get(0).isDelivered());
    }

    @Test
    @DisplayName("T248: the alert store does not erase the report an alert belongs to")
    void alertStoreDoesNotEraseTheReportReference() throws SQLException {
        // AlertService writes the alert through the report first, then through
        // here. An Alert carries no reference to its disaster, so if this
        // store wrote the column it would write null, and the alert would
        // detach from the disaster while still appearing in the list.
        Alert alert = evacuation("AL-0001");
        reports.insert(reportWith(alert));
        assertEquals("DR-0001", storedReferenceFor("AL-0001"));

        repository.save(alert);

        assertEquals("DR-0001", storedReferenceFor("AL-0001"),
                "Saving through the alert store must leave the reference alone.");
    }

    @Test
    @DisplayName("T249: the two writers produce the same result in either order")
    void writerOrderDoesNotMatter() throws SQLException {
        // The other order: the alert store first, then the report.
        Alert alert = evacuation("AL-0001");
        repository.save(alert);
        assertNotNull(repository.findAll().get(0));

        reports.insert(reportWith(alert));

        assertEquals("DR-0001", storedReferenceFor("AL-0001"));
        assertEquals(1, repository.findAll().size(),
                "Two writers must produce one alert, not two.");
    }

    @Test
    @DisplayName("T250: an alert stored with its report is found by the alert store too")
    void alertWrittenByTheReportIsVisibleHere() {
        reports.insert(reportWith(evacuation("AL-0001")));

        List<Alert> all = repository.findAll();

        assertEquals(1, all.size());
        assertEquals("AL-0001", all.get(0).getAlertId());
        assertEquals(AlertType.EVACUATION, all.get(0).getType());
    }

    @Test
    @DisplayName("T251: alerts are listed oldest first")
    void alertsAreOrderedByTimeIssued() throws InterruptedException {
        Alert first = evacuation("AL-0001");
        Thread.sleep(5);
        Alert second = new Alert("AL-0002", AlertType.WARNING, "Riverside",
                "Flooding expected.", "coordinator");

        repository.save(second);
        repository.save(first);

        List<Alert> all = repository.findAll();
        assertEquals("AL-0001", all.get(0).getAlertId(),
                "Order comes from when each was issued, not when it was stored.");
        assertEquals("AL-0002", all.get(1).getAlertId());
    }

    @Test
    @DisplayName("An alert with no report is permitted, because a broadcast still happened")
    void alertWithoutAReportIsPermitted() throws SQLException {
        repository.save(evacuation("AL-0001"));

        assertEquals(1, repository.findAll().size());
        assertEquals(null, storedReferenceFor("AL-0001"));
    }

    @Test
    @DisplayName("A null alert is refused")
    void nullAlertRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }
}
