package com.cqu.drs.service;

import com.cqu.drs.model.Severity;

/**
 * The rules about alerts that need no stored data to answer.
 *
 * Held on their own for the same reason {@link ReportValidation} is: the rule
 * is now wanted in two places, and a rule written twice is a rule that will
 * eventually be two different rules. The client asks it so that the evacuation
 * prompt appears the moment an assessment is recorded, without waiting for a
 * round trip, and the server asks the same method.
 */
public final class AlertRules {

    /** Not instantiable: this class holds one rule. */
    private AlertRules() {
    }

    /**
     * Whether a severity is serious enough that an evacuation should be
     * offered.
     *
     * @param severity the severity assessed
     * @return true when an evacuation alert should be offered
     */
    public static boolean warrantsEvacuation(Severity severity) {
        return severity == Severity.SEVERE;
    }
}
