package com.cqu.drs.model;

/**
 * Whether an allocation still holds its units.
 *
 * Two values rather than deleting the row when the units come back. An
 * allocation that is released is still a record of a decision somebody made
 * during an emergency, and the audit trail refers to it. Deleting it would
 * remove the evidence that the units were ever committed.
 */
public enum AllocationStatus {

    /** The units are committed to a disaster and are out of the pool. */
    HELD("Held"),

    /** The units have been returned to the pool and are available again. */
    RELEASED("Released");

    private final String label;

    AllocationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
