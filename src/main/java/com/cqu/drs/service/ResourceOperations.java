package com.cqu.drs.service;

import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;

import java.util.List;

/**
 * Committing a department's finite resources to a disaster, and getting them
 * back.
 *
 * Implemented twice, like every other set of operations in the system: once
 * by the service that reaches the repository in the same process, and once by
 * the client, which asks the server. The screen calls the same methods either
 * way.
 */
public interface ResourceOperations {

    /**
     * Every pool, whatever is left in it.
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
     * Commits units to a disaster.
     *
     * @param resourceId  the pool to take from
     * @param reference   the disaster they are for
     * @param units       how many are wanted
     * @param requestedBy the coordinator asking
     * @return the allocation recorded
     * @throws IllegalArgumentException when the pool or the disaster is
     *         unknown, or the number of units is not sensible
     * @throws IllegalStateException    when the units are no longer available
     */
    ResourceAllocation allocate(String resourceId, String reference, int units,
                                String requestedBy);

    /**
     * Returns an allocation's units to their pool.
     *
     * @param allocationId the allocation
     * @param releasedBy   the coordinator releasing it
     * @return the allocation as it now stands
     * @throws IllegalArgumentException when the allocation is unknown
     * @throws IllegalStateException    when it was already released
     */
    ResourceAllocation release(String allocationId, String releasedBy);

    /**
     * What is committed to one disaster.
     *
     * @param reference the disaster
     * @return the allocations, held and released alike, newest first
     */
    List<ResourceAllocation> findAllocationsForReport(String reference);

    /**
     * Everything currently held across every disaster, which is how a
     * coordinator sees why a pool is empty.
     *
     * @return the held allocations
     */
    List<ResourceAllocation> findHeldAllocations();
}
