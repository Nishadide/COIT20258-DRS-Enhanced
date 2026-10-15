package com.cqu.drs.client;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.db.SchemaInitialiser;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.MySqlDisasterReportRepository;
import com.cqu.drs.server.DrsServer;
import com.cqu.drs.server.RequestDispatcher;
import com.cqu.drs.server.SessionRegistry;
import com.cqu.drs.server.handler.FindReportByReferenceHandler;
import com.cqu.drs.server.handler.SubmitReportHandler;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.ReportOperations;
import com.cqu.drs.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T209 to T214: the walking skeleton.
 *
 * One operation travelling the whole way, from a client holding no database
 * connection, over a socket, through the server, into MySQL, and back. This is
 * the test that closes the project's second gate, and the only one that
 * exercises all three tiers at once.
 *
 * <p>It needs a database and skips without one, like the other MySQL tests,
 * and works in {@code drs_enhanced_test} so that it cannot touch the
 * application's data.
 *
 * <p>The audit trail is still written to a JSON file here, as it is on the
 * real server until the MySQL audit repository lands. That is not what is
 * being tested, and the temporary folder keeps it out of the way.
 */
@DisplayName("Walking skeleton: client to server to MySQL")
class WalkingSkeletonTest {

    private static final String TEST_DATABASE = "drs_enhanced_test";

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private DrsServer server;
    private DrsClient client;
    private RemoteReportService remote;

    @BeforeEach
    void startEverything(@TempDir Path serverData) throws SQLException, IOException {
        originalDatabaseName = System.getProperty(DatabaseConfig.KEY_DB_NAME);
        System.setProperty(DatabaseConfig.KEY_DB_NAME, TEST_DATABASE);

        DatabaseConfig config = DatabaseConfig.load();
        ConnectionFactory candidate = new ConnectionFactory(config);

        Assumptions.assumeTrue(isReachable(candidate),
                "No MySQL database is reachable at " + config.describeTarget()
                        + ", so the walking skeleton test is skipped.");

        connections = candidate;
        new SchemaInitialiser(connections).initialise();
        clearReports();

        ReportOperations reportService = new ReportService(
                new MySqlDisasterReportRepository(connections),
                new AuditService(new JsonAuditRepository(new JsonDataStore(serverData))));

        RequestDispatcher dispatcher =
                new RequestDispatcher(new SessionRegistry(), new InMemoryRequestLogRepository())
                        .register(new SubmitReportHandler(reportService))
                        .register(new FindReportByReferenceHandler(reportService));

        server = new DrsServer(0, 8, dispatcher);
        server.start();

        client = new DrsClient("127.0.0.1", server.getPort());
        remote = new RemoteReportService(client);
    }

    @AfterEach
    void stopEverything() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
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

    private void clearReports() throws SQLException {
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

    /**
     * Reads a report straight out of MySQL with plain SQL.
     *
     * Deliberately not through the repository. Using the repository to check
     * what the repository stored would only prove the two halves of one class
     * agree with each other.
     *
     * @param reference the reference to look for
     * @return the row as text, or null when there is no such row
     * @throws SQLException when the query fails
     */
    private String readRowDirectly(String reference) throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT type, address, status, reported_severity FROM disaster_reports"
                     + " WHERE reference_number = '" + reference + "'")) {
            if (!results.next()) {
                return null;
            }
            return results.getString(1) + "|" + results.getString(2) + "|"
                    + results.getString(3) + "|" + results.getString(4);
        }
    }

    private int countReports() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT COUNT(*) FROM disaster_reports")) {
            return results.next() ? results.getInt(1) : 0;
        }
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T209: a report lodged on the client arrives as a row in MySQL")
    void reportReachesMySql() throws SQLException {
        String reference = remote.submitReport(DisasterType.FIRE,
                "12 Quay Street, Rockhampton", -23.377723, 150.510742,
                Severity.HIGH, "Smoke from the top floor.",
                new Reporter("RP-clientside", "A. Reporter", "0412345678"));

        assertEquals("DR-0001", reference);

        String row = readRowDirectly(reference);
        assertNotNull(row, "No row was written to MySQL.");
        assertEquals("FIRE|12 Quay Street, Rockhampton|SUBMITTED|HIGH", row);
    }

    @Test
    @DisplayName("T210: the report comes back from MySQL through the server to the client")
    void reportComesBackFromMySql() {
        String reference = remote.submitReport(DisasterType.FLOOD, "Riverside Drive",
                null, null, Severity.MEDIUM, "Water over the road.", null);

        DisasterReport read = remote.findByReference(reference).orElseThrow();

        assertEquals(reference, read.getReferenceNumber());
        assertEquals(DisasterType.FLOOD, read.getType());
        assertEquals("Water over the road.", read.getDescription());
        assertTrue(read.isAnonymous());
    }

    @Test
    @DisplayName("T211: the client holds no database connection of its own")
    void clientHoldsNoDatabaseConnection() throws SQLException {
        // The point of three tiers. The client reached the data without any
        // means of reaching the database: it has a socket and nothing else.
        // Stopping the server proves the client cannot get there alone.
        String reference = remote.submitReport(DisasterType.OTHER, "Somewhere",
                null, null, Severity.LOW, null, null);
        assertEquals(1, countReports());

        server.stop();
        client.close();

        org.junit.jupiter.api.Assertions.assertThrows(ServerUnavailableException.class,
                () -> remote.findByReference(reference));
    }

    @Test
    @DisplayName("T212: the reporter is stored in MySQL with a server-allocated identifier")
    void reporterIsStoredInMySql() throws SQLException {
        remote.submitReport(DisasterType.FIRE, "Mill Lane", null, null,
                Severity.HIGH, null, new Reporter("RP-clientside", "B. Witness", "0498765432"));

        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT reporter_id, name, contact_number FROM reporters")) {

            assertTrue(results.next(), "No reporter row was written.");
            assertTrue(results.getString("reporter_id").startsWith("RP-"));
            org.junit.jupiter.api.Assertions.assertNotEquals("RP-clientside",
                    results.getString("reporter_id"),
                    "The identifier must be the server's, not the client's.");
            assertEquals("B. Witness", results.getString("name"));
            assertEquals("0498765432", results.getString("contact_number"));
        }
    }

    @Test
    @DisplayName("T213: four clients lodging reports at once all get different references")
    void concurrentClientsGetDistinctReferences() throws Exception {
        // This is the race that commit 9 made visible and this commit handles.
        // References are generated by counting the reports already stored, so
        // four clients submitting together will collide. The database refuses
        // the duplicate and the service takes the next free reference.
        //
        // Without the retry, some of these submissions would fail. Without
        // insert refusing duplicates, they would succeed by overwriting one
        // another and reports would silently disappear.
        int clients = 4;
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch go = new CountDownLatch(1);
        List<String> references = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < clients; i++) {
            final int number = i;
            Thread thread = new Thread(() -> {
                try (DrsClient own = new DrsClient("127.0.0.1", server.getPort())) {
                    RemoteReportService service = new RemoteReportService(own);
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    references.add(service.submitReport(DisasterType.FIRE,
                            "Street " + number, null, null, Severity.HIGH, null, null));
                } catch (Exception e) {
                    failures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
            threads.add(thread);
            thread.start();
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "Clients did not all connect.");
        // Released together, so the submissions overlap rather than queue.
        go.countDown();
        for (Thread thread : threads) {
            thread.join(15000);
        }

        assertTrue(failures.isEmpty(), "Some submissions failed: " + failures);
        assertEquals(clients, references.size(), "Every client must get a reference.");
        assertEquals(clients, Set.copyOf(references).size(),
                "Two clients were given the same reference: " + references);
        assertEquals(clients, countReports(),
                "Every report must be stored. A smaller number means one "
                        + "overwrote another.");
    }

    @Test
    @DisplayName("T214: every stored reference has the documented DR-nnnn form")
    void referencesKeepTheirDocumentedForm() throws SQLException {
        remote.submitReport(DisasterType.FIRE, "One", null, null, Severity.LOW, null, null);
        remote.submitReport(DisasterType.FLOOD, "Two", null, null, Severity.LOW, null, null);

        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT reference_number FROM disaster_reports ORDER BY reference_number")) {
            List<String> found = new ArrayList<>();
            while (results.next()) {
                found.add(results.getString(1));
            }
            assertEquals(List.of("DR-0001", "DR-0002"), Collections.unmodifiableList(found));
        }
    }
}
