package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.AllocationStatus;
import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import com.cqu.drs.model.ResourceType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores the resource pools in MySQL, and decides allocations inside a
 * transaction.
 *
 * <h2>How two coordinators competing for the last crew are ordered</h2>
 * {@link #allocate} opens a transaction and reads the pool with
 * {@code SELECT ... FOR UPDATE}. That lock is the whole mechanism. The second
 * transaction to arrive does not read a stale count and decide on it: it waits
 * at the select until the first commits, and then reads what is actually left.
 * One coordinator is given the crew and the other is told there are none,
 * which is the correct outcome and the one the feature exists to demonstrate.
 *
 * <p>The decision and the write are in the same transaction, so there is no
 * window between them. Nothing in Java is relied on for this. A synchronized
 * block would order two threads inside one server and would do nothing at all
 * about two servers, or about a second copy of the application run from
 * another machine, which is exactly the arrangement three tiers invites.
 *
 * <h2>Why a deadlock is retried rather than reported</h2>
 * Two transactions taking the same rows in different orders can deadlock, and
 * InnoDB resolves it by rolling one back. That is not a fault and it is not
 * the coordinator's problem: the rolled back transaction is expected to try
 * again, and on the retry it waits normally. Reporting it would mean telling
 * somebody their request failed because somebody else made one at the same
 * moment.
 */
public final class MySqlResourceRepository implements ResourceRepository {

    /** Two transactions wanted the same rows and InnoDB rolled one back. */
    private static final int DEADLOCK = 1213;

    /** A transaction waited for a lock longer than the server allows. */
    private static final int LOCK_WAIT_TIMEOUT = 1205;

    /**
     * How many times an allocation is retried after a deadlock.
     *
     * Allocations are serialised by a row lock, so under load they queue
     * rather than deadlock. Three attempts covers the case where a request
     * loses the race twice and still stops rather than spinning if something
     * that is not contention is wrong.
     */
    private static final int MAX_ATTEMPTS = 3;

    private static final String SELECT_RESOURCES = """
            SELECT resource_id, department_id, type, name, total_units, available_units
              FROM resources""";

    private static final String SELECT_FOR_UPDATE = """
            SELECT resource_id, department_id, type, name, total_units, available_units
              FROM resources
             WHERE resource_id = ?
               FOR UPDATE""";

    private static final String UPSERT_RESOURCE = """
            INSERT INTO resources
              (resource_id, department_id, type, name, total_units, available_units)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE department_id = VALUES(department_id),
                                    type = VALUES(type),
                                    name = VALUES(name),
                                    total_units = VALUES(total_units),
                                    available_units = VALUES(available_units)""";

    private static final String TAKE_UNITS = """
            UPDATE resources
               SET available_units = available_units - ?
             WHERE resource_id = ?""";

    private static final String RETURN_UNITS = """
            UPDATE resources
               SET available_units = available_units + ?
             WHERE resource_id = ?""";

    private static final String INSERT_ALLOCATION = """
            INSERT INTO resource_allocations
              (allocation_id, resource_id, reference_number, units, status,
               requested_by, allocated_at, released_by, released_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)""";

    private static final String SELECT_ALLOCATIONS = """
            SELECT a.allocation_id, a.resource_id, a.reference_number, a.units,
                   a.status, a.requested_by, a.allocated_at, a.released_by,
                   a.released_at, r.name AS resource_name, r.type AS resource_type
              FROM resource_allocations a
              JOIN resources r ON r.resource_id = a.resource_id""";

    private static final String SELECT_ALLOCATION_FOR_UPDATE = """
            SELECT allocation_id, resource_id, units, status
              FROM resource_allocations
             WHERE allocation_id = ?
               FOR UPDATE""";

    private static final String MARK_RELEASED = """
            UPDATE resource_allocations
               SET status = 'RELEASED', released_by = ?, released_at = ?
             WHERE allocation_id = ?""";

    private static final String COUNT = "SELECT COUNT(*) FROM resources";

    private static final String REPORT_EXISTS =
            "SELECT 1 FROM disaster_reports WHERE reference_number = ?";

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlResourceRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    @Override
    public List<Resource> findAll() {
        return readResources(SELECT_RESOURCES + " ORDER BY department_id, name", null);
    }

    @Override
    public List<Resource> findByDepartment(String departmentId) {
        return readResources(SELECT_RESOURCES + " WHERE department_id = ? ORDER BY name",
                departmentId);
    }

    @Override
    public Optional<Resource> findById(String resourceId) {
        if (resourceId == null) {
            return Optional.empty();
        }
        List<Resource> found =
                readResources(SELECT_RESOURCES + " WHERE resource_id = ?", resourceId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public void save(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot save a null resource.");
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT_RESOURCE)) {

            statement.setString(1, resource.getResourceId());
            statement.setString(2, resource.getDepartmentId());
            statement.setString(3, resource.getType().name());
            statement.setString(4, resource.getName());
            statement.setInt(5, resource.getTotalUnits());
            statement.setInt(6, resource.getAvailableUnits());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("The resource " + resource.getName()
                    + " could not be saved. " + e.getMessage(), e);
        }
    }

    @Override
    public boolean seedIfEmpty() {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(COUNT)) {

            if (results.next() && results.getInt(1) > 0) {
                return false;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The resource pools could not be counted. " + e.getMessage(), e);
        }

        for (Resource resource : ResourceSeed.pools()) {
            save(resource);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * The order inside the transaction is deliberate. The pool is locked
     * first, then the disaster is checked, then the units are taken and the
     * allocation written. Locking first means the check and the write see one
     * unchanging pool; checking the disaster inside the same transaction means
     * a report cannot be resolved out from under an allocation being made
     * against it.
     */
    @Override
    public ResourceAllocation allocate(String resourceId, String reference, int units,
                                       String requestedBy) {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "The number of units requested must be at least one.");
        }

        SQLException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Connection connection = null;
            try {
                connection = connections.openTransaction();

                Resource resource = lockResource(connection, resourceId);
                requireReportExists(connection, reference);

                if (resource.getAvailableUnits() < units) {
                    // Read with the row locked, so this is what is actually
                    // there rather than what was there a moment ago. The
                    // rollback releases the lock at once: another coordinator
                    // waiting behind this one should not be held up by a
                    // request that is being refused.
                    connection.rollback();
                    throw new InsufficientResourcesException(resource.getName(), units,
                            resource.getAvailableUnits());
                }

                try (PreparedStatement take = connection.prepareStatement(TAKE_UNITS)) {
                    take.setInt(1, units);
                    take.setString(2, resourceId);
                    take.executeUpdate();
                }

                resource.claim(units);

                ResourceAllocation allocation = new ResourceAllocation(
                        "RA-" + UUID.randomUUID().toString().substring(0, 8),
                        resource, reference, units, requestedBy);
                allocation.setAllocatedAt(
                        allocation.getAllocatedAt().truncatedTo(ChronoUnit.MICROS));

                try (PreparedStatement insert =
                             connection.prepareStatement(INSERT_ALLOCATION)) {
                    insert.setString(1, allocation.getAllocationId());
                    insert.setString(2, allocation.getResourceId());
                    insert.setString(3, allocation.getReferenceNumber());
                    insert.setInt(4, allocation.getUnits());
                    insert.setString(5, allocation.getStatus().name());
                    insert.setString(6, allocation.getRequestedBy());
                    insert.setObject(7, allocation.getAllocatedAt());
                    insert.executeUpdate();
                }

                connection.commit();
                return allocation;

            } catch (SQLException e) {
                rollback(connection);
                if (!isRetryable(e)) {
                    throw new IllegalStateException("The allocation could not be made. "
                            + e.getMessage(), e);
                }
                lastFailure = e;

            } finally {
                close(connection);
            }
        }

        throw new IllegalStateException("The allocation could not be made after "
                + MAX_ATTEMPTS + " attempts because of contention for the same "
                + "resources.", lastFailure);
    }

    @Override
    public ResourceAllocation release(String allocationId, String releasedBy) {
        Connection connection = null;
        try {
            connection = connections.openTransaction();

            String resourceId;
            int units;

            try (PreparedStatement locked =
                         connection.prepareStatement(SELECT_ALLOCATION_FOR_UPDATE)) {
                locked.setString(1, allocationId);
                try (ResultSet results = locked.executeQuery()) {
                    if (!results.next()) {
                        connection.rollback();
                        throw new IllegalArgumentException(
                                "No allocation holds the identifier " + allocationId + ".");
                    }
                    if (AllocationStatus.RELEASED.name()
                            .equals(results.getString("status"))) {
                        // Refused with the row locked, so two simultaneous
                        // releases cannot both pass this check and put the
                        // units back twice.
                        connection.rollback();
                        throw new IllegalStateException("That allocation has already "
                                + "been released, so its units are already back in "
                                + "the pool.");
                    }
                    resourceId = results.getString("resource_id");
                    units = results.getInt("units");
                }
            }

            try (PreparedStatement give = connection.prepareStatement(RETURN_UNITS)) {
                give.setInt(1, units);
                give.setString(2, resourceId);
                give.executeUpdate();
            }

            LocalDateTime releasedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
            try (PreparedStatement mark = connection.prepareStatement(MARK_RELEASED)) {
                mark.setString(1, releasedBy);
                mark.setObject(2, releasedAt);
                mark.setString(3, allocationId);
                mark.executeUpdate();
            }

            connection.commit();

            ResourceAllocation released = findAllocationById(allocationId).orElseThrow(
                    () -> new IllegalStateException("The allocation vanished after "
                            + "being released."));
            return released;

        } catch (SQLException e) {
            rollback(connection);
            throw new IllegalStateException("The allocation could not be released. "
                    + e.getMessage(), e);
        } finally {
            close(connection);
        }
    }

    @Override
    public Optional<ResourceAllocation> findAllocationById(String allocationId) {
        if (allocationId == null) {
            return Optional.empty();
        }
        List<ResourceAllocation> found = readAllocations(
                SELECT_ALLOCATIONS + " WHERE a.allocation_id = ?", allocationId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public List<ResourceAllocation> findAllocationsForReport(String reference) {
        return readAllocations(SELECT_ALLOCATIONS
                + " WHERE a.reference_number = ? ORDER BY a.allocated_at DESC", reference);
    }

    @Override
    public List<ResourceAllocation> findHeldAllocations() {
        return readAllocations(SELECT_ALLOCATIONS
                + " WHERE a.status = 'HELD' ORDER BY a.allocated_at DESC", null);
    }

    // ---- internals -----------------------------------------------------

    private Resource lockResource(Connection connection, String resourceId)
            throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(SELECT_FOR_UPDATE)) {
            statement.setString(1, resourceId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    connection.rollback();
                    throw new IllegalArgumentException(
                            "No resource holds the identifier " + resourceId + ".");
                }
                return readResource(results);
            }
        }
    }

    /**
     * Refuses an allocation against a disaster that does not exist.
     *
     * The foreign key would refuse it too, a moment later, with a message
     * naming a constraint. Checking here means the coordinator is told which
     * reference was not found, which is the thing they can act on.
     *
     * @param connection the open transaction
     * @param reference  the disaster named
     * @throws SQLException when the query fails
     */
    private void requireReportExists(Connection connection, String reference)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(REPORT_EXISTS)) {
            statement.setString(1, reference);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    connection.rollback();
                    throw new IllegalArgumentException(
                            "No report holds the reference " + reference + ".");
                }
            }
        }
    }

    private List<Resource> readResources(String sql, String parameter) {
        List<Resource> resources = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    resources.add(readResource(results));
                }
            }
            return resources;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The resources could not be read. " + e.getMessage(), e);
        }
    }

    private static Resource readResource(ResultSet results) throws SQLException {
        Resource resource = new Resource(results.getString("resource_id"),
                results.getString("department_id"),
                ResourceType.valueOf(results.getString("type")),
                results.getString("name"),
                results.getInt("total_units"));
        // The constructor starts a pool with everything available, which is
        // true of a new pool and not of a stored one.
        resource.setAvailableUnits(results.getInt("available_units"));
        return resource;
    }

    private List<ResourceAllocation> readAllocations(String sql, String parameter) {
        List<ResourceAllocation> allocations = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    allocations.add(readAllocation(results));
                }
            }
            return allocations;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The allocations could not be read. " + e.getMessage(), e);
        }
    }

    private static ResourceAllocation readAllocation(ResultSet results)
            throws SQLException {
        // The restoring constructor, not the one that makes an allocation:
        // that one stamps the current time and sets the status to held, both
        // of which are wrong for a row being read back.
        ResourceAllocation allocation = new ResourceAllocation(
                results.getString("allocation_id"),
                results.getString("resource_id"),
                results.getString("resource_name"),
                ResourceType.valueOf(results.getString("resource_type")),
                results.getString("reference_number"),
                results.getInt("units"),
                AllocationStatus.valueOf(results.getString("status")),
                results.getString("requested_by"),
                results.getObject("allocated_at", LocalDateTime.class));
        allocation.setReleasedBy(results.getString("released_by"));
        allocation.setReleasedAt(results.getObject("released_at", LocalDateTime.class));
        return allocation;
    }

    private static boolean isRetryable(SQLException e) {
        return e.getErrorCode() == DEADLOCK || e.getErrorCode() == LOCK_WAIT_TIMEOUT;
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            System.err.println("The allocation transaction could not be rolled back: "
                    + e.getMessage());
        }
    }

    private static void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (SQLException e) {
            System.err.println("The connection did not close cleanly: " + e.getMessage());
        }
    }
}
