package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A quantity of one resource committed to one disaster.
 *
 * <h2>Why this is a record of a decision rather than a number on a report</h2>
 * The units could have been tracked by storing a count against the disaster.
 * They are stored as their own records because each one answers questions a
 * count cannot: who committed these units, when, from which pool, and are they
 * still committed. During a response those are the questions asked when a
 * second disaster needs the same crews, and afterwards they are the questions
 * asked when somebody reviews how the response was run.
 *
 * <p>The timestamps are written by the server, never by a client, for the same
 * reason every other timestamp in the system is: a client's clock can be wrong
 * or dishonest, and a record ordered by client clocks establishes nothing.
 */
public class ResourceAllocation {

    private String allocationId;
    private String resourceId;
    private String resourceName;
    private ResourceType resourceType;
    private String referenceNumber;
    private int units;
    private AllocationStatus status;
    private String requestedBy;
    private LocalDateTime allocatedAt;
    private String releasedBy;
    private LocalDateTime releasedAt;

    /**
     * Creates an allocation that holds its units.
     *
     * @param allocationId    unique identifier
     * @param resource        the pool the units came from
     * @param referenceNumber the disaster they are committed to
     * @param units           how many
     * @param requestedBy     the coordinator who asked for them
     */
    public ResourceAllocation(String allocationId, Resource resource,
                              String referenceNumber, int units, String requestedBy) {
        if (resource == null) {
            throw new IllegalArgumentException("An allocation must name a resource.");
        }
        if (units <= 0) {
            throw new IllegalArgumentException("An allocation must be of at least one unit.");
        }
        this.allocationId = allocationId;
        this.resourceId = resource.getResourceId();

        // The name and type are copied onto the allocation rather than looked
        // up through the identifier. A responder's screen listing what is
        // committed to a disaster would otherwise need the pool as well, and
        // the copy is also the record of what the resource was called at the
        // time, which is what a review of the response wants to see.
        this.resourceName = resource.getName();
        this.resourceType = resource.getType();

        this.referenceNumber = referenceNumber;
        this.units = units;
        this.status = AllocationStatus.HELD;
        this.requestedBy = requestedBy;
        this.allocatedAt = LocalDateTime.now();
    }

    /** No-argument constructor required by Gson. */
    protected ResourceAllocation() {
    }

    /**
     * Rebuilds an allocation that was stored earlier.
     *
     * Separate from the constructor above because that one is for an
     * allocation being made: it stamps the current time and sets the status to
     * held, both of which are wrong for a row being read back. A repository
     * that used it would have to undo its work with setters, and the time
     * would be briefly wrong in an object other code might already be reading.
     *
     * @param allocationId    the identifier it was stored under
     * @param resourceId      the pool it came from
     * @param resourceName    what that pool was called
     * @param resourceType    the kind of resource
     * @param referenceNumber the disaster it is for
     * @param units           how many
     * @param status          whether it is still held
     * @param requestedBy     who asked for it
     * @param allocatedAt     when it was made
     */
    public ResourceAllocation(String allocationId, String resourceId, String resourceName,
                              ResourceType resourceType, String referenceNumber, int units,
                              AllocationStatus status, String requestedBy,
                              LocalDateTime allocatedAt) {
        this.allocationId = allocationId;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.referenceNumber = referenceNumber;
        this.units = units;
        this.status = status;
        this.requestedBy = requestedBy;
        this.allocatedAt = allocatedAt;
    }

    public String getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(String allocationId) {
        this.allocationId = allocationId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public int getUnits() {
        return units;
    }

    public void setUnits(int units) {
        this.units = units;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public void setStatus(AllocationStatus status) {
        this.status = status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(LocalDateTime allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(String releasedBy) {
        this.releasedBy = releasedBy;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    /**
     * Whether these units are still committed.
     *
     * @return true when the allocation has not been released
     */
    public boolean isHeld() {
        return status == AllocationStatus.HELD;
    }

    /**
     * Marks the units as returned to their pool.
     *
     * @param releasedBy the coordinator returning them
     * @throws IllegalStateException when the allocation was already released,
     *         which would otherwise put the units back twice
     */
    public void markReleased(String releasedBy) {
        if (status == AllocationStatus.RELEASED) {
            throw new IllegalStateException("This allocation was already released at "
                    + releasedAt + ".");
        }
        this.status = AllocationStatus.RELEASED;
        this.releasedBy = releasedBy;
        this.releasedAt = LocalDateTime.now();
    }

    /**
     * A description for a table or an audit entry.
     *
     * @return text such as "3 crews of Heavy pumper for DR-0001"
     */
    public String describe() {
        String quantity = resourceType == null
                ? units + " units" : resourceType.describe(units);
        return quantity + " of " + resourceName + " for " + referenceNumber;
    }

    @Override
    public String toString() {
        return describe() + " [" + (status == null ? "unknown" : status.getLabel()) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceAllocation)) {
            return false;
        }
        return Objects.equals(allocationId, ((ResourceAllocation) o).allocationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allocationId);
    }
}
