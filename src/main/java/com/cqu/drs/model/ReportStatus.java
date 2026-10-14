package com.cqu.drs.model;

/**
 * The states a disaster report passes through during its lifetime. The legal
 * transitions are held here so that a report cannot be moved into a state that
 * does not follow from the one it is in.
 */
public enum ReportStatus {

    SUBMITTED("Submitted"),
    ASSESSED("Assessed"),
    DISPATCHED("Dispatched"),
    RESOLVED("Resolved");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Reports whether this status may legally be followed by the one given.
     *
     * @param next the status being moved to
     * @return true when the transition is allowed
     */
    public boolean canMoveTo(ReportStatus next) {
        return switch (this) {
            case SUBMITTED -> next == ASSESSED;
            case ASSESSED -> next == DISPATCHED;
            case DISPATCHED -> next == RESOLVED;
            case RESOLVED -> false;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
