package com.cqu.drs.model;

/**
 * The kinds of public alert an Emergency Coordinator may issue.
 */
public enum AlertType {

    WARNING("Warning"),
    EVACUATION("Evacuation");

    private final String label;

    AlertType(String label) {
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
