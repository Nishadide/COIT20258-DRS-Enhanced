package com.cqu.drs.migration;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.repository.DatabaseTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T302 to T305: the ledger that stops a migration happening twice.
 *
 * These need a database, because what is being tested is that the record
 * survives in MySQL rather than in a field: a ledger that forgot on restart
 * would let the migration run again, which is the one thing it exists to
 * prevent. They skip when no database is reachable, like the other repository
 * tests.
 *
 * <p>Only this ledger's own rows are removed between tests. The rest of
 * schema_metadata belongs to the schema's history, and a test that emptied the
 * table would be destroying a record of what had been applied to the database
 * in order to check something unrelated to it.
 */
@DisplayName("The migration ledger in MySQL")
class MySqlMigrationLedgerTest {

    private static final String ITEM = "test.migration.item";

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlMigrationLedger ledger;

    @BeforeEach
    void connect() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();
        removeTestItems();
        ledger = new MySqlMigrationLedger(connections);
    }

    @AfterEach
    void disconnect() throws SQLException {
        if (connections != null) {
            removeTestItems();
        }
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    private void removeTestItems() throws SQLException {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM schema_metadata WHERE item LIKE 'test.%'")) {
            statement.executeUpdate();
        }
    }

    @Test
    @DisplayName("T302: an item that has not run reads as not run")
    void unrecordedItemReadsAsNotRun() {
        // The quiet case first. A ledger that answered true for everything
        // would pass every other test here while blocking every migration.
        assertFalse(ledger.hasRun(ITEM));
        assertTrue(ledger.detailOf(ITEM).isEmpty());
    }

    @Test
    @DisplayName("T303: a recorded item reads back with its detail and the time")
    void recordedItemReadsBack() {
        ledger.record(ITEM, "41 records carried over, requested by admin.");

        assertTrue(ledger.hasRun(ITEM));
        String detail = ledger.detailOf(ITEM).orElseThrow();

        assertTrue(detail.contains("41 records carried over"), detail);
        assertTrue(detail.contains("Applied at"),
                "When it happened is part of the answer: " + detail);
    }

    @Test
    @DisplayName("T304: recording the same item twice does not fail")
    void recordingTwiceDoesNotFail() {
        ledger.record(ITEM, "first");
        ledger.record(ITEM, "second");

        assertTrue(ledger.detailOf(ITEM).orElseThrow().contains("second"),
                "The later record should replace the earlier one rather than "
                        + "being refused by the primary key.");
    }

    @Test
    @DisplayName("T305: one item says nothing about another")
    void itemsAreIndependent() {
        ledger.record(ITEM, "done");

        assertFalse(ledger.hasRun("test.migration.something.else"));
        assertThrows(IllegalArgumentException.class, () -> ledger.record(null, "x"));
    }

    @Test
    @DisplayName("T306: the real migration's item name is the one asked about twice")
    void migrationItemNameIsStable() {
        // The name is a constant rather than something built at run time,
        // because the question asked on the second run has to be the same
        // question that was answered on the first. A name containing a date or
        // a path would make every run a different migration.
        assertEquals("data.migration.drs-initial", DataMigrationService.LEDGER_ITEM);
    }
}
