package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.security.FieldCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T348 to T355: casualty records in MySQL, with the personal fields
 * encrypted.
 *
 * <h2>The test that matters most here</h2>
 * T349 reads the stored columns with plain SQL, the way somebody with a copy
 * of the database would, and checks that the name is not in them. Every other
 * test in this class could pass with the encryption switched off: a round trip
 * through the repository would return the name whether or not anything was
 * encrypted on the way. Only looking at the raw column proves the claim the
 * report makes.
 */
@DisplayName("Casualty records in MySQL")
class MySqlCasualtyRepositoryTest {

    private static final String REFERENCE = "DR-9100";
    private static final String NAME = "Priya Raman";
    private static final String CONTACT = "0412345678";
    private static final String NOTES = "Compound fracture, left leg.";

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlCasualtyRepository repository;

    @BeforeEach
    void connectAndPrepare() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();

        clearCasualties();
        DatabaseTestSupport.clearAll(connections);
        insertReport();

        repository = new MySqlCasualtyRepository(connections,
                new FieldCipher(FieldCipher.newKey()));
    }

    @AfterEach
    void disconnect() throws SQLException {
        if (connections != null) {
            clearCasualties();
        }
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    private void clearCasualties() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM casualties");
        }
    }

    private void insertReport() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO disaster_reports "
                    + "(reference_number, type, address, reported_at, status) VALUES ('"
                    + REFERENCE + "', 'EARTHQUAKE', 'Test Street', NOW(6), 'ASSESSED')");
        }
    }

    private Casualty register(String name, TriageCategory triage) {
        Casualty casualty = new Casualty("CA-" + System.nanoTime() % 100000000L,
                REFERENCE, name, 34, CONTACT, triage, "Ward entrance", NOTES,
                "responder");
        repository.save(casualty);
        return casualty;
    }

    /**
     * Reads the stored columns exactly as somebody holding a copy of the
     * database would.
     *
     * @param casualtyId the record
     * @return the three stored values joined, or null when there is no row
     * @throws SQLException when the query fails
     */
    private String readStoredColumns(String casualtyId) throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT name_enc, contact_enc, notes_enc FROM casualties"
                             + " WHERE casualty_id = '" + casualtyId + "'")) {
            if (!results.next()) {
                return null;
            }
            return results.getString(1) + "|" + results.getString(2)
                    + "|" + results.getString(3);
        }
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T348: a casualty comes back from the database intact")
    void recordRoundTrips() {
        Casualty saved = register(NAME, TriageCategory.IMMEDIATE);

        Casualty read = repository.findById(saved.getCasualtyId()).orElseThrow();

        assertEquals(NAME, read.getName());
        assertEquals(CONTACT, read.getContactNumber());
        assertEquals(NOTES, read.getNotes());
        assertEquals(34, read.getAge());
        assertEquals(TriageCategory.IMMEDIATE, read.getTriage());
        assertEquals(CasualtyStatus.REGISTERED, read.getStatus());
        assertEquals("Ward entrance", read.getLocation());
    }

    @Test
    @DisplayName("T349: the name is not readable in the database")
    void storedColumnsHoldCiphertext() throws SQLException {
        Casualty saved = register(NAME, TriageCategory.DELAYED);

        String stored = readStoredColumns(saved.getCasualtyId());
        assertNotNull(stored, "No row was written.");

        assertFalse(stored.contains(NAME),
                "The casualty's name is sitting readable in the database: " + stored);
        assertFalse(stored.contains(CONTACT),
                "The contact number is readable in the database: " + stored);
        assertFalse(stored.contains("fracture"),
                "The notes are readable in the database: " + stored);

        assertTrue(FieldCipher.looksEncrypted(stored.split("\\|")[0]),
                "The stored name is not in the encrypted form at all.");
    }

    @Test
    @DisplayName("T350: two casualties with the same name store different ciphertext")
    void sameNameStoresDifferently() throws SQLException {
        Casualty first = register(NAME, TriageCategory.MINOR);
        Casualty second = register(NAME, TriageCategory.MINOR);

        assertFalse(readStoredColumns(first.getCasualtyId())
                        .equals(readStoredColumns(second.getCasualtyId())),
                "Identical stored values would tell anybody holding the table "
                        + "that these two records share a name.");
    }

    @Test
    @DisplayName("T351: a record altered in the database is refused, not shown")
    void alteredRecordIsRefused() throws SQLException {
        Casualty saved = register(NAME, TriageCategory.IMMEDIATE);

        // Somebody editing the database directly, which is the threat
        // encryption at rest is for. GCM authenticates what it encrypts, so
        // this is detected rather than returning a different name.
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE casualties SET name_enc = CONCAT("
                    + "SUBSTRING(name_enc, 1, CHAR_LENGTH(name_enc) - 1), 'X')"
                    + " WHERE casualty_id = '" + saved.getCasualtyId() + "'");
        }

        assertThrows(IllegalStateException.class,
                () -> repository.findById(saved.getCasualtyId()));
    }

    @Test
    @DisplayName("T352: an unidentified casualty is stored with no name at all")
    void unidentifiedCasualtyStoresNull() throws SQLException {
        Casualty saved = register(null, TriageCategory.DECEASED);

        Casualty read = repository.findById(saved.getCasualtyId()).orElseThrow();
        assertNull(read.getName());
        assertTrue(read.getDisplayName().startsWith("Unidentified"));

        String stored = readStoredColumns(saved.getCasualtyId());
        assertTrue(stored.startsWith("null|"),
                "An absent name must be NULL, not the ciphertext of an empty "
                        + "string, which would make every unidentified casualty "
                        + "look like the same person: " + stored);
    }

    @Test
    @DisplayName("T353: the most urgent casualty is listed first")
    void listIsOrderedByUrgency() {
        register("Minor one", TriageCategory.MINOR);
        register("Deceased one", TriageCategory.DECEASED);
        register("Immediate one", TriageCategory.IMMEDIATE);
        register("Delayed one", TriageCategory.DELAYED);

        var ordered = repository.findForReport(REFERENCE);

        assertEquals(4, ordered.size());
        assertEquals(TriageCategory.IMMEDIATE, ordered.get(0).getTriage());
        assertEquals(TriageCategory.DELAYED, ordered.get(1).getTriage());
        assertEquals(TriageCategory.MINOR, ordered.get(2).getTriage());
        assertEquals(TriageCategory.DECEASED, ordered.get(3).getTriage());
    }

    @Test
    @DisplayName("T354: the summary counts every category, including the empty ones")
    void summaryCountsEveryCategory() {
        register("One", TriageCategory.IMMEDIATE);
        register("Two", TriageCategory.IMMEDIATE);
        register("Three", TriageCategory.MINOR);

        Map<TriageCategory, Integer> counts = repository.countByTriage(REFERENCE);

        assertEquals(4, counts.size(), "Every category must be present.");
        assertEquals(2, counts.get(TriageCategory.IMMEDIATE));
        assertEquals(1, counts.get(TriageCategory.MINOR));
        assertEquals(0, counts.get(TriageCategory.DELAYED),
                "A category with nobody in it must report zero rather than "
                        + "being left out, which reads as the figure not having "
                        + "been collected.");
        assertEquals(0, counts.get(TriageCategory.DECEASED));
    }

    @Test
    @DisplayName("T355: saving a casualty twice updates the record rather than duplicating it")
    void saveIsAnUpsert() {
        Casualty saved = register(NAME, TriageCategory.DELAYED);

        saved.retriage(TriageCategory.IMMEDIATE, "coordinator");
        repository.save(saved);

        assertEquals(1, repository.count());
        assertEquals(TriageCategory.IMMEDIATE,
                repository.findById(saved.getCasualtyId()).orElseThrow().getTriage());
    }
}
