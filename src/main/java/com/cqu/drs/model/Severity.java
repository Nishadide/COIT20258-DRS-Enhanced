package com.cqu.drs.model;

/**
 * Assessed severity of a disaster. Each level maps to exactly one response
 * priority, and that mapping lives here with the data it describes rather than
 * as a conditional in the service layer.
 */
public enum Severity {

    SEVERE("Life at immediate risk, or a large area affected"),
    HIGH("Serious harm or damage occurring or likely"),
    MEDIUM("Harm contained, with no immediate threat to life"),
    LOW("Minor incident with no threat to life");

    private final String description;

    Severity(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Maps this severity to the response priority it warrants.
     *
     * @return the priority for this severity level
     */
    public Priority toPriority() {
        return switch (this) {
            case SEVERE -> Priority.P1;
            case HIGH -> Priority.P2;
            case MEDIUM -> Priority.P3;
            case LOW -> Priority.P4;
        };
    }

    @Override
    public String toString() {
        return name();
    }
}
