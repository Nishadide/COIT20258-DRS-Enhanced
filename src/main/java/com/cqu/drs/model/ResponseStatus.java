package com.cqu.drs.model;

/**
 * The states a dispatched response task passes through. A task starts as
 * DISPATCHED when it is sent to a department and ends as COMPLETE.
 */
public enum ResponseStatus {

    DISPATCHED("Dispatched"),
    IN_PROGRESS("In progress"),
    COMPLETE("Complete");

    private final String label;

    ResponseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
