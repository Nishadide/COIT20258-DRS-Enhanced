package com.cqu.drs.service;

import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.Priority;
import com.cqu.drs.model.Severity;

import java.util.List;
import java.util.Optional;

/**
 * Judging how serious a reported disaster is, and the queue that follows from
 * it.
 */
public interface AssessmentOperations {

    /**
     * Records a coordinator's judgement of a disaster.
     *
     * @param reference  the report being assessed
     * @param severity   how serious it is
     * @param notes      optional notes
     * @param assessedBy the coordinator's username
     * @return the assessment recorded
     */
    Assessment assess(String reference, Severity severity, String notes, String assessedBy);

    /**
     * The response priority an assessment warrants.
     *
     * Derived from the severity rather than stored, so the two can never
     * disagree, and answered without contacting the server because it needs
     * nothing but the assessment already in hand.
     *
     * @param assessment the assessment
     * @return the priority
     */
    Priority prioritise(Assessment assessment);

    /**
     * Assessed reports in the order they should be dealt with.
     *
     * @return the reports, most urgent first
     */
    List<DisasterReport> queueByPriority();

    /**
     * Reports that nobody has assessed yet.
     *
     * @return the reports
     */
    List<DisasterReport> awaitingAssessment();

    /**
     * Closes a report once its work is finished.
     *
     * @param reference  the report to close
     * @param resolvedBy the coordinator's username
     */
    void resolve(String reference, String resolvedBy);

    /**
     * Finds one report by its reference.
     *
     * @param reference the reference to look for
     * @return the report, or an empty Optional
     */
    Optional<DisasterReport> findByReference(String reference);
}
