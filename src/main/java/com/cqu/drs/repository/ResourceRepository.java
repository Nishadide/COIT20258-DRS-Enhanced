package com.cqu.drs.repository;

import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;

import java.util.List;
import java.util.Optional;

/**
 * Where the resource pools and what has been committed from them are stored.
 *
 * <h2>Why allocating is a repository method and not a service method</h2>
 * Everywhere else in this system the service decides and the repository
 * stores. Allocation is the exception, and deliberately so: deciding whether
 * the units are there and taking them have to be one indivisible act, and the
 * only thing in the system that can make two operations indivisible is a
 * database transaction.
 *
 * <p>Were the service to read the pool, decide, and then ask the repository to
 * write, two coordinators could both read four crews available, both decide
 * three were obtainable, and the pool would end up holding minus two. No
 * amount of care in the service prevents that. A Java lock would prevent it
 * only while exactly one server process runs, and a design that is correct
 * only while one server runs is not a three-tier design.
 *
 * <p>So {@link #allocate} is handed the whole question and answers it with the
 * row locked. The service above it keeps what it is good at: validating the
 * request, recording the audit entry, and turning a refusal into something a
 * coordinator can read.
 */
public interface ResourceRepository {

    /**
     * Every pool.
     *
     * @return the resources
     */
    List<Resource> findAll();

    /**
     * The pools one department holds.
     *
     * @param departmentId the department
     * @return the resources
     */
    List<Resource> findByDepartment(String departmentId);

    /**
     * Finds one pool.
     *
     * @param resourceId the identifier
     * @return the resource, or an empty Optional
     */
    Optional<Resource> findById(String resourceId);

    /**
     * Stores a pool, inserting it or replacing what is held.
     *
     * Used to set a pool up or to change its total. Never used to take units
     * out of it: that is {@link #allocate}, which does it where it can be done
     * safely.
     *
     * @param resource the resource to store
     */
    void save(Resource resource);

    /**
     * Creates the starting pools when none exist.
     *
     * @return true when pools were created
     */
    boolean seedIfEmpty();

    /**
     * Commits units from a pool to a disaster, or refuses because they are no
     * longer there.
     *
     * This is one indivisible operation. Whichever request reaches the row
     * first takes the units; the second waits, reads what is actually left,
     * and is refused if that is not enough.
     *
     * @param resourceId  the pool to take from
     * @param reference   the disaster the units are for
     * @param units       how many are wanted
     * @param requestedBy the coordinator asking
     * @return the allocation recorded
     * @throws IllegalArgumentException          when the pool or the disaster
     *                                           does not exist
     * @throws InsufficientResourcesException    when the units are not there
     */
    ResourceAllocation allocate(String resourceId, String reference, int units,
                                String requestedBy);

    /**
     * Returns an allocation's units to its pool.
     *
     * Also one indivisible operation, and it refuses a second release of the
     * same allocation rather than putting the units back twice.
     *
     * @param allocationId the allocation to release
     * @param releasedBy   the coordinator releasing it
     * @return the allocation as it now stands
     * @throws IllegalArgumentException when no such allocation exists
     * @throws IllegalStateException    when it was already released
     */
    ResourceAllocation release(String allocationId, String releasedBy);

    /**
     * Finds one allocation.
     *
     * @param allocationId the identifier
     * @return the allocation, or an empty Optional
     */
    Optional<ResourceAllocation> findAllocationById(String allocationId);

    /**
     * What is committed to one disaster, newest first.
     *
     * @param reference the disaster
     * @return the allocations, held and released alike
     */
    List<ResourceAllocation> findAllocationsForReport(String reference);

    /**
     * Everything currently held, across every disaster.
     *
     * @return the allocations that have not been released
     */
    List<ResourceAllocation> findHeldAllocations();
}
