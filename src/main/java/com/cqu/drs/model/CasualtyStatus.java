package com.cqu.drs.model;

/**
 * Where a casualty is in the response, as distinct from how urgent they are.
 *
 * Triage says how serious the injuries are; this says what has happened since.
 * The two change independently: a casualty triaged as immediate may be treated
 * on site, and one triaged as minor may still be referred because the site is
 * being evacuated.
 */
public enum CasualtyStatus {

    /** Recorded at the scene, nothing done yet. */
    REGISTERED("Registered"),

    /** Treated where they were found and not transported. */
    TREATED_ON_SITE("Treated on site"),

    /** Sent to a hospital, which is recorded against the casualty. */
    REFERRED("Referred to hospital"),

    /** No longer in the system's care. */
    DISCHARGED("Discharged");

    private final String label;

    CasualtyStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
