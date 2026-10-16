package com.cqu.drs.service;

import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import com.cqu.drs.repository.InsufficientResourcesException;
import com.cqu.drs.repository.ResourceRepository;

import java.util.List;

/**
 * Committing finite resources to disasters.
 *
 * <h2>What this service does and what it refuses to do</h2>
 * It validates the request, asks the repository to make the allocation, and
 * records what happened in the audit trail. What it does not do is decide
 * whether the units are available, because that decision and the write that
 * follows it have to be indivisible, and nothing above a database transaction
 * can make them so.
 *
 * <p>That is unusual in this system, where services decide and repositories
 * store, and it is worth being explicit about. A service that read the pool,
 * found four crews, and then asked for three would be deciding on a number
 * that another coordinator may already have spent. The repository makes the
 * decision with the row locked, and this service's job is to turn the answer
 * into something a person can read.
 *
 * <h2>Both outcomes are recorded</h2>
 * A refused allocation is written to the audit trail as well as a successful
 * one. During a response, "we asked for the last two crews and were told they
 * were gone" is exactly the kind of thing a review afterwards needs, and a
 * trail holding only what succeeded would show the request had never been
 * made.
 */
public class ResourceService implements ResourceOperations {

    private final ResourceRepository resourceRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param resourceRepository where the pools and allocations are stored
     * @param auditService       where allocations are recorded
     */
    public ResourceService(ResourceRepository resourceRepository,
                           AuditService auditService) {
        if (resourceRepository == null || auditService == null) {
            throw new IllegalArgumentException(
                    "A resource service needs a repository and an audit service.");
        }
        this.resourceRepository = resourceRepository;
        this.auditService = auditService;
    }

    @Override
    public List<Resource> findAll() {
        return resourceRepository.findAll();
    }

    @Override
    public List<Resource> findByDepartment(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            throw new IllegalArgumentException("Name the department whose "
                    + "resources you want.");
        }
        return resourceRepository.findByDepartment(departmentId);
    }

    /**
     * {@inheritDoc}
     *
     * The validation here is about what was asked for, not about whether it
     * can be granted. A request for zero units or for a disaster with no
     * reference is wrong however much of the pool is free, and refusing it
     * before opening a transaction keeps the lock held for less time.
     */
    @Override
    public ResourceAllocation allocate(String resourceId, String reference, int units,
                                       String requestedBy) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Choose a resource to allocate.");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(
                    "Name the disaster the resources are for.");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("Ask for at least one unit.");
        }

        try {
            ResourceAllocation allocation = resourceRepository.allocate(
                    resourceId, reference, units, requestedBy);

            auditService.record(requestedBy, "Allocated " + allocation.describe());
            return allocation;

        } catch (InsufficientResourcesException shortfall) {
            // Recorded before it is reported. The refusal is a fact about the
            // response, not an error in the application, and it is the half of
            // the story a trail of successes would leave out.
            auditService.record(requestedBy, "Requested " + units + " units of "
                    + shortfall.getResourceName() + " for " + reference
                    + " but only " + shortfall.getUnitsAvailable() + " were available");

            // Raised as an IllegalStateException because nothing about the
            // request was wrong: the state of the pool changed. The screens
            // already catch it, and the message is the one the coordinator
            // needs.
            throw new IllegalStateException(shortfall.getMessage(), shortfall);
        }
    }

    @Override
    public ResourceAllocation release(String allocationId, String releasedBy) {
        if (allocationId == null || allocationId.isBlank()) {
            throw new IllegalArgumentException("Choose an allocation to release.");
        }

        ResourceAllocation released = resourceRepository.release(allocationId, releasedBy);
        auditService.record(releasedBy, "Released " + released.describe());
        return released;
    }

    @Override
    public List<ResourceAllocation> findAllocationsForReport(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Name the disaster.");
        }
        return resourceRepository.findAllocationsForReport(reference);
    }

    @Override
    public List<ResourceAllocation> findHeldAllocations() {
        return resourceRepository.findHeldAllocations();
    }
}
