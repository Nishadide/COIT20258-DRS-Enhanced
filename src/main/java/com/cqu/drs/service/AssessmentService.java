package com.cqu.drs.service;

import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.Priority;
import com.cqu.drs.model.ReportStatus;
import com.cqu.drs.model.Severity;
import com.cqu.drs.repository.DisasterReportRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assesses reported disasters and orders them for response. The priority is
 * never chosen by hand: it follows from the severity recorded, so the two can
 * never disagree.
 */
public class AssessmentService implements AssessmentOperations {

    private final DisasterReportRepository reportRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param reportRepository where reports are stored
     * @param auditService     used to record assessments
     */
    public AssessmentService(DisasterReportRepository reportRepository,
                             AuditService auditService) {
        this.reportRepository = reportRepository;
        this.auditService = auditService;
    }

    /**
     * Records an assessment against a report and moves it on to be coordinated.
     *
     * @param reference  the report being assessed
     * @param severity   the severity judged
     * @param notes      optional notes
     * @param assessedBy the username of the coordinator
     * @return the assessment recorded
     * @throws IllegalArgumentException when the report cannot be found
     * @throws IllegalStateException    when the report is not awaiting assessment
     */
    @Override
    public Assessment assess(String reference, Severity severity, String notes,
                             String assessedBy) {
        DisasterReport report = reportRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No report holds the reference " + reference + "."));

        Assessment assessment = new Assessment(
                UUID.randomUUID().toString(), severity, notes, assessedBy);
        report.attachAssessment(assessment);
        reportRepository.save(report);

        auditService.record(assessedBy, "Assessed " + reference + " as " + severity
                + ", priority " + assessment.derivePriority().name());
        return assessment;
    }

    /**
     * The priority an assessment warrants.
     *
     * @param assessment the assessment
     * @return the derived priority
     */
    @Override
    public Priority prioritise(Assessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("Cannot prioritise a null assessment.");
        }
        return assessment.derivePriority();
    }

    /**
     * Reports that have been assessed but not yet resolved, most urgent first.
     * Ordering is by the priority rank rather than the enum position, so
     * reordering the enum cannot change the queue.
     *
     * @return the reports awaiting or undergoing a response, in priority order
     */
    @Override
    public List<DisasterReport> queueByPriority() {
        return reportRepository.findAll().stream()
                .filter(DisasterReport::isAssessed)
                .filter(r -> r.currentStatus() != ReportStatus.RESOLVED)
                .sorted(Comparator.comparingInt(r -> r.getPriority().getRank()))
                .collect(Collectors.toList());
    }

    /**
     * Reports that have been lodged but not yet assessed, oldest first.
     *
     * @return the reports awaiting assessment
     */
    @Override
    public List<DisasterReport> awaitingAssessment() {
        return reportRepository.findAll().stream()
                .filter(r -> !r.isAssessed())
                .sorted(Comparator.comparing(DisasterReport::getReportedAt))
                .collect(Collectors.toList());
    }

    /**
     * Closes a report once its response is finished.
     *
     * @param reference  the report to close
     * @param resolvedBy the username of the coordinator
     * @throws IllegalArgumentException when the report cannot be found
     * @throws IllegalStateException    when work remains outstanding
     */
    @Override
    public void resolve(String reference, String resolvedBy) {
        DisasterReport report = reportRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No report holds the reference " + reference + "."));
        report.resolve();
        reportRepository.save(report);
        auditService.record(resolvedBy, "Resolved " + reference);
    }

    /**
     * Finds a report by reference.
     *
     * @param reference the reference to look for
     * @return the report, or an empty Optional
     */
    @Override
    public Optional<DisasterReport> findByReference(String reference) {
        return reportRepository.findByReference(reference);
    }
}
