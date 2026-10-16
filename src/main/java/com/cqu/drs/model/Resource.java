package com.cqu.drs.model;

import java.util.Objects;

/**
 * A finite pool of something a department can send: crews, vehicles, hospital
 * beds, shelter places.
 *
 * <h2>Why the available count is stored rather than derived</h2>
 * It could be computed by summing the allocations that have not been released.
 * It is stored because the whole point of this feature is that two
 * coordinators can want the last unit at the same moment, and a stored count
 * is a single row the database can lock. Deriving it would mean reading every
 * allocation, deciding, and writing, with the decision made on data that may
 * already be stale by the time the write happens.
 *
 * <p>The two numbers are kept honest by the database as well as by this
 * class: the table carries a check constraint saying the available count lies
 * between zero and the total, so a fault in this code cannot produce a pool
 * with minus one crew in it.
 *
 * <h2>What this class does not do</h2>
 * It does not decide whether an allocation succeeds. {@link #claim} and
 * {@link #release} change the object in memory and nothing else. The decision
 * belongs to the repository, inside a transaction that has the row locked,
 * because a decision made in Java protects one server process and this is a
 * system designed to survive more than one.
 */
public class Resource {

    private String resourceId;
    private String departmentId;
    private ResourceType type;
    private String name;
    private int totalUnits;
    private int availableUnits;

    /**
     * Creates a pool with every unit available.
     *
     * @param resourceId   unique identifier
     * @param departmentId the department that holds it
     * @param type         the kind of resource
     * @param name         what it is called, such as "Heavy pumper"
     * @param totalUnits   how many units exist
     */
    public Resource(String resourceId, String departmentId, ResourceType type,
                    String name, int totalUnits) {
        if (type == null) {
            throw new IllegalArgumentException("A resource must have a type.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A resource must have a name.");
        }
        if (totalUnits < 0) {
            throw new IllegalArgumentException(
                    "A resource cannot have a negative number of units.");
        }
        this.resourceId = resourceId;
        this.departmentId = departmentId;
        this.type = type;
        this.name = name.trim();
        this.totalUnits = totalUnits;
        this.availableUnits = totalUnits;
    }

    /** No-argument constructor required by Gson. */
    protected Resource() {
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalUnits() {
        return totalUnits;
    }

    public void setTotalUnits(int totalUnits) {
        this.totalUnits = totalUnits;
    }

    public int getAvailableUnits() {
        return availableUnits;
    }

    public void setAvailableUnits(int availableUnits) {
        this.availableUnits = availableUnits;
    }

    /**
     * How many units are currently out on an allocation.
     *
     * @return the committed count
     */
    public int getCommittedUnits() {
        return totalUnits - availableUnits;
    }

    /**
     * Whether this pool could satisfy a request of the given size.
     *
     * Answering true here is not permission to proceed. Between this question
     * and the write that follows it, another coordinator may have taken the
     * units. It is used to grey out a control and to fail fast, and the
     * binding answer is given by the repository with the row locked.
     *
     * @param units how many are wanted
     * @return true when that many are available right now
     */
    public boolean canSatisfy(int units) {
        return units > 0 && units <= availableUnits;
    }

    /**
     * Takes units out of the pool.
     *
     * @param units how many
     * @throws IllegalArgumentException when units is not positive
     * @throws IllegalStateException    when there are not that many available
     */
    public void claim(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "The number of units requested must be at least one.");
        }
        if (units > availableUnits) {
            throw new IllegalStateException(name + " has only "
                    + type.describe(availableUnits) + " available, and "
                    + units + " were requested.");
        }
        availableUnits -= units;
    }

    /**
     * Puts units back into the pool.
     *
     * @param units how many
     * @throws IllegalArgumentException when units is not positive
     * @throws IllegalStateException    when returning them would exceed the
     *                                  total this pool holds, which would mean
     *                                  something had been released twice
     */
    public void release(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "The number of units released must be at least one.");
        }
        if (availableUnits + units > totalUnits) {
            throw new IllegalStateException("Releasing " + units + " to " + name
                    + " would leave more units available than exist, so the "
                    + "allocation has probably been released already.");
        }
        availableUnits += units;
    }

    @Override
    public String toString() {
        return name + " (" + type.describe(availableUnits) + " of "
                + totalUnits + " available)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Resource)) {
            return false;
        }
        return Objects.equals(resourceId, ((Resource) o).resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceId);
    }
}
