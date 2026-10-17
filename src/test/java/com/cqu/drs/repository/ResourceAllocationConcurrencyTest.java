package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import com.cqu.drs.model.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T324 to T330: two coordinators, one unit.
 *
 * <h2>What these tests are for</h2>
 * The resource allocation feature exists to demonstrate the multi-threaded
 * server, and the claim being demonstrated is precise: when several clients
 * ask for the same units at the same moment, the pool is never oversubscribed
 * and exactly as many requests succeed as there were units to give.
 *
 * <p>That claim cannot be checked by allocating twice in a row. It needs real
 * threads arriving at the same moment, which is what the latch arranges: every
 * thread is built, started, and then held at the same line until the latch
 * drops, so they enter the repository together rather than one after another.
 *
 * <p>They need a database, because what is being tested is the database doing
 * the ordering. They skip without one, like the other MySQL tests.
 */
@DisplayName("Competing for the last unit")
class ResourceAllocationConcurrencyTest {

    private static final String RESOURCE_ID = "RS-TEST-POOL";
    private static final String REFERENCE = "DR-9001";

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlResourceRepository repository;

    @BeforeEach
    void connectAndPrepare() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();
        clearResourceTables();
        DatabaseTestSupport.clearAll(connections);

        repository = new MySqlResourceRepository(connections);

        // A department and a disaster for the foreign keys to point at.
        new MySqlDepartmentRepository(connections).seedIfEmpty();
        insertReport(REFERENCE);
    }

    @AfterEach
    void disconnect() throws SQLException {
        if (connections != null) {
            clearResourceTables();
        }
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    private void clearResourceTables() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM resource_allocations");
            statement.executeUpdate("DELETE FROM resources");
        }
    }

    private void insertReport(String reference) throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO disaster_reports "
                    + "(reference_number, type, address, reported_at, status) VALUES ('"
                    + reference + "', 'FIRE', 'Test Street', NOW(6), 'SUBMITTED')");
        }
    }

    private void givePool(int units) {
        repository.save(new Resource(RESOURCE_ID, "DEP-01", ResourceType.PERSONNEL,
                "Test crew", units));
    }

    /**
     * Reads the available count with plain SQL rather than through the
     * repository, so that what is checked is what the database holds and not
     * what the class under test says it holds.
     *
     * @return the available units
     * @throws SQLException when the query fails
     */
    private int readAvailableDirectly() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT available_units FROM resources WHERE resource_id = '"
                             + RESOURCE_ID + "'")) {
            return results.next() ? results.getInt(1) : -1;
        }
    }

    private int countHeldUnitsDirectly() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT COALESCE(SUM(units), 0) FROM resource_allocations"
                             + " WHERE resource_id = '" + RESOURCE_ID
                             + "' AND status = 'HELD'")) {
            return results.next() ? results.getInt(1) : -1;
        }
    }

    /**
     * Runs several allocations at the same instant and reports how many
     * succeeded.
     *
     * @param threads       how many coordinators are competing
     * @param unitsEach     how many units each one asks for
     * @return the allocations that succeeded
     */
    private List<ResourceAllocation> competeFor(int threads, int unitsEach)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ResourceAllocation>> attempts = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String who = "coordinator" + i;
            attempts.add(() -> {
                // Every thread waits here, so they enter the repository
                // together. Without this they would queue naturally and the
                // test would pass whether or not the locking worked.
                start.await(5, TimeUnit.SECONDS);
                return repository.allocate(RESOURCE_ID, REFERENCE, unitsEach, who);
            });
        }

        List<Future<ResourceAllocation>> futures = new ArrayList<>();
        for (Callable<ResourceAllocation> attempt : attempts) {
            futures.add(pool.submit(attempt));
        }
        start.countDown();

        List<ResourceAllocation> granted = new ArrayList<>();
        AtomicInteger refused = new AtomicInteger();

        for (Future<ResourceAllocation> future : futures) {
            try {
                granted.add(future.get(20, TimeUnit.SECONDS));
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof InsufficientResourcesException) {
                    refused.incrementAndGet();
                } else {
                    throw new AssertionError("An allocation failed for a reason other "
                            + "than the units being gone: " + e.getCause(), e.getCause());
                }
            }
        }

        pool.shutdownNow();
        assertEquals(threads, granted.size() + refused.get(),
                "Every attempt must either succeed or be refused.");
        return granted;
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T324: one coordinator allocating from a full pool succeeds")
    void singleAllocationSucceeds() throws SQLException {
        // The quiet case first. A repository that refused everything would
        // pass every concurrency test below while being useless.
        givePool(4);

        ResourceAllocation allocation =
                repository.allocate(RESOURCE_ID, REFERENCE, 3, "coordinator");

        assertEquals(3, allocation.getUnits());
        assertTrue(allocation.isHeld());
        assertEquals(1, readAvailableDirectly(),
                "Three of four units should have been taken.");
    }

    @Test
    @DisplayName("T325: six coordinators want the one remaining unit and one gets it")
    void sixCompeteForOneUnit() throws Exception {
        givePool(1);

        List<ResourceAllocation> granted = competeFor(6, 1);

        assertEquals(1, granted.size(),
                "Exactly one of the six may be given the last unit.");
        assertEquals(0, readAvailableDirectly(),
                "The pool must be empty, not negative.");
        assertEquals(1, countHeldUnitsDirectly(),
                "Exactly one unit may be recorded as held.");
    }

    @Test
    @DisplayName("T326: eight coordinators share a pool of three without oversubscribing it")
    void eightCompeteForThreeUnits() throws Exception {
        givePool(3);

        List<ResourceAllocation> granted = competeFor(8, 1);

        assertEquals(3, granted.size(),
                "Three units means three successful allocations, no more and no fewer.");
        assertEquals(0, readAvailableDirectly());
        assertEquals(3, countHeldUnitsDirectly());
    }

    @Test
    @DisplayName("T327: the stored count and the held allocations always agree")
    void storedCountAgreesWithAllocations() throws Exception {
        givePool(5);

        competeFor(4, 2);

        int available = readAvailableDirectly();
        int held = countHeldUnitsDirectly();

        // The stored count exists so that it can be locked. The price of
        // storing it is that it could disagree with the allocations it is
        // meant to summarise, so that is checked rather than assumed.
        assertEquals(5, available + held,
                "available (" + available + ") plus held (" + held
                        + ") must always be the size of the pool.");
    }

    @Test
    @DisplayName("T328: a refusal says how many units were actually left")
    void refusalReportsWhatIsLeft() {
        givePool(2);
        repository.allocate(RESOURCE_ID, REFERENCE, 2, "coordinator");

        InsufficientResourcesException refused =
                assertThrows(InsufficientResourcesException.class,
                        () -> repository.allocate(RESOURCE_ID, REFERENCE, 1, "other"));

        assertEquals(0, refused.getUnitsAvailable());
        assertEquals(1, refused.getUnitsRequested());
        assertEquals("Test crew", refused.getResourceName());
    }

    @Test
    @DisplayName("T329: releasing returns the units and they can be allocated again")
    void releasingReturnsTheUnits() throws SQLException {
        givePool(2);
        ResourceAllocation allocation =
                repository.allocate(RESOURCE_ID, REFERENCE, 2, "coordinator");
        assertEquals(0, readAvailableDirectly());

        ResourceAllocation released =
                repository.release(allocation.getAllocationId(), "coordinator");

        assertFalse(released.isHeld());
        assertEquals(2, readAvailableDirectly());
        assertEquals(0, countHeldUnitsDirectly());

        // And the units really are usable again, not merely counted.
        assertEquals(2, repository.allocate(RESOURCE_ID, REFERENCE, 2, "other").getUnits());
    }

    @Test
    @DisplayName("T330: releasing the same allocation twice is refused")
    void doubleReleaseIsRefused() throws SQLException {
        givePool(2);
        ResourceAllocation allocation =
                repository.allocate(RESOURCE_ID, REFERENCE, 1, "coordinator");
        repository.release(allocation.getAllocationId(), "coordinator");

        assertThrows(IllegalStateException.class,
                () -> repository.release(allocation.getAllocationId(), "coordinator"));

        assertEquals(2, readAvailableDirectly(),
                "A second release must not put the units back twice, which would "
                        + "leave the pool claiming more units than the department has.");
    }
}
