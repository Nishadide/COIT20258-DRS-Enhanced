package com.cqu.drs.model;

/**
 * The kinds of disaster the system accepts. The case study names hurricanes,
 * fires and earthquakes; flood and a catch-all value complete the set.
 */
public enum DisasterType {

    HURRICANE("Hurricane"),
    FIRE("Fire"),
    EARTHQUAKE("Earthquake"),
    FLOOD("Flood"),
    OTHER("Other");

    private final String label;

    DisasterType(String label) {
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
