package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A reported disaster, and the root of everything recorded about it. The
 * assessment, the response tasks and the alerts all belong to one report and
 * have no meaning apart from it, so the report owns them and is the only way
 * they are reached.
 *
 * The reporter is optional. Reporting is open to the public, so a report may
 * carry no reporter at all.
 */
public class DisasterReport {

    private String referenceNumber;
    private DisasterType type;
    private Location location;
    private String description;
    private Severity reportedSeverity;
    private LocalDateTime reportedAt;
    private ReportStatus status;
    private Reporter reporter;
    private Assessment assessment;
    private List<ResponseTask> tasks;
    private List<Alert> alerts;

    /**
     * Creates a report in the SUBMITTED state, stamped with the current time.
     *
     * @param referenceNumber  the system-generated reference
     * @param type             the kind of disaster
     * @param location         where it happened
     * @param reportedSeverity how serious the reporter judged it
     * @param description      optional further detail
     * @param reporter         the reporter, or null for an anonymous report
     */
    public DisasterReport(String referenceNumber, DisasterType type, Location location,
                          Severity reportedSeverity, String description, Reporter reporter) {
        if (type == null) {
            throw new IllegalArgumentException("A report must name a disaster type.");
        }
        if (location == null) {
            throw new IllegalArgumentException("A report must give a location.");
        }
        this.referenceNumber = referenceNumber;
        this.type = type;
        this.location = location;
        this.reportedSeverity = reportedSeverity;
        setDescription(description);
        this.reporter = reporter;
        this.reportedAt = LocalDateTime.now();
        this.status = ReportStatus.SUBMITTED;
        this.tasks = new ArrayList<>();
        this.alerts = new ArrayList<>();
    }

    /** No-argument constructor required by Gson. */
    protected DisasterReport() {
        this.tasks = new ArrayList<>();
        this.alerts = new ArrayList<>();
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public DisasterType getType() {
        return type;
    }

    public void setType(DisasterType type) {
        this.type = type;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public final void setDescription(String description) {
        if (description != null && description.length() > DrsConstants.DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("Description may be at most "
                    + DrsConstants.DESCRIPTION_MAX_LENGTH + " characters.");
        }
        this.description = (description == null || description.trim().isEmpty())
                ? null : description.trim();
    }

    public Severity getReportedSeverity() {
        return reportedSeverity;
    }

    public void setReportedSeverity(Severity reportedSeverity) {
        this.reportedSeverity = reportedSeverity;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public Reporter getReporter() {
        return reporter;
    }

    public void setReporter(Reporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Reports whether this report was lodged without a named reporter.
     *
     * @return true when no reporter is held, or the reporter is anonymous
     */
    public boolean isAnonymous() {
        return reporter == null || reporter.isAnonymous();
    }

    public Assessment getAssessment() {
        return assessment;
    }

    /**
     * Reports whether a coordinator has assessed this disaster.
     *
     * @return true once an assessment is attached
     */
    public boolean isAssessed() {
        return assessment != null;
    }

    /**
     * The current status of the report.
     *
     * @return the status
     */
    public ReportStatus currentStatus() {
        return status;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    /**
     * The priority derived from the assessment, or null while the report is
     * still unassessed.
     *
     * @return the priority, or null
     */
    public Priority getPriority() {
        return assessment == null ? null : assessment.derivePriority();
    }

    /**
     * Attaches an assessment and moves the report to ASSESSED.
     *
     * @param assessment the assessment recorded
     * @throws IllegalStateException if the report is not awaiting assessment
     */
    public void attachAssessment(Assessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("Cannot attach a null assessment.");
        }
        if (!status.canMoveTo(ReportStatus.ASSESSED)) {
            throw new IllegalStateException("A report with status " + status
                    + " cannot be assessed.");
        }
        this.assessment = assessment;
        this.status = ReportStatus.ASSESSED;
    }

    /**
     * The response tasks dispatched for this report, oldest first.
     *
     * @return an unmodifiable view of the tasks
     */
    public List<ResponseTask> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        return Collections.unmodifiableList(tasks);
    }

    /**
     * Dispatches a task for this report, moving it to DISPATCHED the first
     * time a task is added.
     *
     * @param task the task dispatched
     * @throws IllegalStateException if the report has not been assessed
     */
    public void addTask(ResponseTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Cannot add a null task.");
        }
        if (!isAssessed()) {
            throw new IllegalStateException(
                    "A report must be assessed before work can be dispatched.");
        }
        if (status == ReportStatus.RESOLVED) {
            throw new IllegalStateException("A resolved report cannot receive new work.");
        }
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        tasks.add(task);
        if (status == ReportStatus.ASSESSED) {
            this.status = ReportStatus.DISPATCHED;
        }
    }

    /**
     * The alerts issued for this report.
     *
     * @return an unmodifiable view of the alerts
     */
    public List<Alert> getAlerts() {
        if (alerts == null) {
            alerts = new ArrayList<>();
        }
        return Collections.unmodifiableList(alerts);
    }

    /**
     * Records an alert against this report.
     *
     * @param alert the alert issued
     */
    public void addAlert(Alert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("Cannot add a null alert.");
        }
        if (alerts == null) {
            alerts = new ArrayList<>();
        }
        alerts.add(alert);
    }

    /**
     * Reports whether every dispatched task has finished.
     *
     * @return true when no task remains open
     */
    public boolean allTasksComplete() {
        if (tasks == null || tasks.isEmpty()) {
            return true;
        }
        return tasks.stream().allMatch(ResponseTask::isComplete);
    }

    /**
     * Closes the report. This is refused while any dispatched task is still
     * open, so that a response cannot be signed off with work outstanding.
     *
     * @throws IllegalStateException if work remains or the status does not allow closing
     */
    public void resolve() {
        if (!status.canMoveTo(ReportStatus.RESOLVED)) {
            throw new IllegalStateException("A report with status " + status
                    + " cannot be resolved.");
        }
        if (!allTasksComplete()) {
            throw new IllegalStateException(
                    "The report cannot be resolved while response tasks remain open.");
        }
        this.status = ReportStatus.RESOLVED;
    }

    @Override
    public String toString() {
        Priority p = getPriority();
        return referenceNumber + "  " + type.getLabel() + " at " + location
                + "  [" + status + (p == null ? "" : ", " + p.name()) + "]"
                + (isAnonymous() ? "  (anonymous)" : "  reported by " + reporter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisasterReport)) {
            return false;
        }
        return Objects.equals(referenceNumber, ((DisasterReport) o).referenceNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceNumber);
    }
}
