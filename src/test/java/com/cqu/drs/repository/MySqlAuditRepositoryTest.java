package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.AuditLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T215 to T226: the hash-chained audit trail.
 *
 * This is where the non-repudiation requirement stops being a claim. It is not
 * enough to show that entries are stored and can be read back; the point of a
 * chain is that altering it is detectable, so most of these tests alter the
 * trail behind the application's back, with plain SQL, and check that the
 * verification notices.
 *
 * A chain that has never been tested against tampering has not been shown to
 * do the one thing it exists for.
 */
@DisplayName("MySqlAuditRepository")
class MySqlAuditRepositoryTest {

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlAuditRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();
        DatabaseTestSupport.clearAll(connections);
        repository = new MySqlAuditRepository(connections);
    }

    @AfterEach
    void tearDown() {
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    private AuditLogEntry entry(String actor, String action) {
        return new AuditLogEntry("AE-" + UUID.randomUUID().toString().substring(0, 8),
                actor, action);
    }

    private void appendThree() {
        repository.append(entry("admin", "Created account coordinator"));
        repository.append(entry("coordinator", "Assessed DR-0001 as SEVERE"));
        repository.append(entry("fire.crew", "Marked RT-0001 complete"));
    }

    /** Runs one statement directly, standing in for someone with database access. */
    private void tamper(String sql) throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    // ---- storing and reading -------------------------------------------

    @Test
    @DisplayName("T215: an entry is stored and read back with its actor, action and time")
    void entryRoundTrips() {
        AuditLogEntry original = entry("admin", "Created account coordinator");
        repository.append(original);

        List<AuditLogEntry> all = repository.findAll();

        assertEquals(1, all.size());
        assertEquals("admin", all.get(0).getActor());
        assertEquals("Created account coordinator", all.get(0).getAction());
        assertEquals(original.getTimestamp(), all.get(0).getTimestamp());
    }

    @Test
    @DisplayName("T216: entries come back oldest first, whatever order they were written")
    void entriesAreOrdered() {
        appendThree();

        List<AuditLogEntry> all = repository.findAll();

        assertEquals(3, all.size());
        assertEquals("Created account coordinator", all.get(0).getAction());
        assertEquals("Assessed DR-0001 as SEVERE", all.get(1).getAction());
        assertEquals("Marked RT-0001 complete", all.get(2).getAction());
    }

    @Test
    @DisplayName("T217: the first entry chains from sixty-four zeros")
    void firstEntryUsesTheGenesisHash() throws SQLException {
        repository.append(entry("admin", "Started the system"));

        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             var results = statement.executeQuery(
                     "SELECT sequence_no, previous_hash, entry_hash FROM audit_log")) {

            assertTrue(results.next());
            assertEquals(1, results.getLong("sequence_no"));
            assertEquals(AuditChain.GENESIS_HASH, results.getString("previous_hash"));
            assertEquals(64, results.getString("entry_hash").length());
        }
    }

    @Test
    @DisplayName("T218: each entry records the hash of the one before it")
    void eachEntryChainsOntoItsPredecessor() throws SQLException {
        appendThree();

        List<String> hashes = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             var results = statement.executeQuery(
                     "SELECT previous_hash, entry_hash FROM audit_log ORDER BY sequence_no")) {
            while (results.next()) {
                previous.add(results.getString("previous_hash"));
                hashes.add(results.getString("entry_hash"));
            }
        }

        assertEquals(AuditChain.GENESIS_HASH, previous.get(0));
        assertEquals(hashes.get(0), previous.get(1));
        assertEquals(hashes.get(1), previous.get(2));
    }

    // ---- verification ---------------------------------------------------

    @Test
    @DisplayName("T219: an untouched trail verifies")
    void untouchedTrailVerifies() {
        appendThree();

        AuditChainStatus status = repository.verifyChain();

        assertTrue(status.supported());
        assertTrue(status.intact(), status.detail());
        assertEquals(3, status.entriesChecked());
        assertEquals(-1, status.brokenAtSequence());
    }

    @Test
    @DisplayName("T220: an empty trail verifies rather than failing")
    void emptyTrailVerifies() {
        AuditChainStatus status = repository.verifyChain();

        assertTrue(status.intact());
        assertEquals(0, status.entriesChecked());
    }

    @Test
    @DisplayName("T221: altering what an entry says breaks the chain at that entry")
    void alteredActionIsDetected() throws SQLException {
        appendThree();

        // Someone with database access rewriting history: the coordinator's
        // assessment changed from SEVERE to LOW after the fact.
        tamper("UPDATE audit_log SET action = 'Assessed DR-0001 as LOW' "
                + "WHERE sequence_no = 2");

        AuditChainStatus status = repository.verifyChain();

        assertFalse(status.intact());
        assertEquals(2, status.brokenAtSequence());
        assertTrue(status.detail().contains("altered"), status.detail());
    }

    @Test
    @DisplayName("T222: changing who did something breaks the chain")
    void alteredActorIsDetected() throws SQLException {
        appendThree();

        // Shifting the blame for an action onto another account.
        tamper("UPDATE audit_log SET actor = 'coordinator' WHERE sequence_no = 1");

        AuditChainStatus status = repository.verifyChain();

        assertFalse(status.intact());
        assertEquals(1, status.brokenAtSequence());
    }

    @Test
    @DisplayName("T223: back-dating an entry breaks the chain")
    void alteredTimestampIsDetected() throws SQLException {
        appendThree();

        tamper("UPDATE audit_log SET timestamp = '2020-01-01 00:00:00.000000' "
                + "WHERE sequence_no = 3");

        AuditChainStatus status = repository.verifyChain();

        assertFalse(status.intact());
        assertEquals(3, status.brokenAtSequence());
    }

    @Test
    @DisplayName("T224: removing an entry is detected by the gap it leaves")
    void deletedEntryIsDetected() throws SQLException {
        appendThree();

        // Deleting the inconvenient middle entry.
        tamper("DELETE FROM audit_log WHERE sequence_no = 2");

        AuditChainStatus status = repository.verifyChain();

        assertFalse(status.intact());
        assertEquals(3, status.brokenAtSequence());
        assertTrue(status.detail().contains("removed"), status.detail());
    }

    @Test
    @DisplayName("T225: re-hashing an altered entry alone does not repair the chain")
    void repairingOneHashIsNotEnough() throws SQLException {
        appendThree();

        // A more careful attacker: alter the entry and put a plausible hash
        // back. It still fails, because the hash they would need is the one
        // the next entry already recorded as its predecessor.
        tamper("UPDATE audit_log SET action = 'Assessed DR-0001 as LOW', "
                + "entry_hash = REPEAT('a', 64) WHERE sequence_no = 2");

        AuditChainStatus status = repository.verifyChain();

        assertFalse(status.intact());
        assertEquals(2, status.brokenAtSequence(),
                "Altering one entry has to break the chain at that entry, "
                        + "whatever hash is put back with it.");
    }

    @Test
    @DisplayName("T226: entries appended concurrently form one unbroken chain")
    void concurrentAppendsChainCorrectly() throws Exception {
        // Each entry's hash depends on the entry before it, so two threads
        // appending at once could read the same predecessor and produce two
        // entries claiming the same place in the chain. The row lock, and the
        // retry for the empty-trail case, are what stop that.
        int writers = 6;
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch go = new CountDownLatch(1);
        List<String> failures = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < writers; i++) {
            final int number = i;
            Thread thread = new Thread(() -> {
                try {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    repository.append(entry("user" + number, "Did thing " + number));
                } catch (Exception e) {
                    failures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
            threads.add(thread);
            thread.start();
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        for (Thread thread : threads) {
            thread.join(20000);
        }

        assertTrue(failures.isEmpty(), "Some appends failed: " + failures);

        AuditChainStatus status = repository.verifyChain();
        assertTrue(status.intact(), status.detail());
        assertEquals(writers, status.entriesChecked(),
                "Every entry must be present and in the chain.");

        // And no two entries claimed the same position.
        List<Long> sequences = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             var results = statement.executeQuery(
                     "SELECT sequence_no FROM audit_log ORDER BY sequence_no")) {
            while (results.next()) {
                sequences.add(results.getLong(1));
            }
        }
        assertEquals(writers, Set.copyOf(sequences).size());
        assertEquals(1L, sequences.get(0).longValue());
        assertEquals((long) writers, sequences.get(writers - 1).longValue());
    }

    @Test
    @DisplayName("A null entry is refused")
    void nullEntryRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.append(null));
    }

    @Test
    @DisplayName("The JSON store reports that it cannot verify, rather than that all is well")
    void jsonStoreAdmitsItKeepsNoChain(@org.junit.jupiter.api.io.TempDir
                                       java.nio.file.Path tempDir) {
        // The honest answer from a store with no chain. Reporting "intact"
        // would be a claim it has no means of supporting.
        AuditChainStatus status =
                new JsonAuditRepository(new JsonDataStore(tempDir)).verifyChain();

        assertFalse(status.supported());
        assertFalse(status.intact());
        assertTrue(status.detail().contains("cannot be determined"), status.detail());
    }
}
