package com.cqu.drs.model;

/**
 * Response priority assigned to an assessed disaster. P1 is dispatched first.
 */
public enum Priority {

    P1(1, "Immediate"),
    P2(2, "Urgent"),
    P3(3, "Standard"),
    P4(4, "Scheduled");

    private final int rank;
    private final String label;

    Priority(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    /**
     * Lower rank means dispatched sooner. Used to order the coordinator queue.
     *
     * @return the ordering rank, 1 being the most urgent
     */
    public int getRank() {
        return rank;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return name() + " (" + label + ")";
    }
}
