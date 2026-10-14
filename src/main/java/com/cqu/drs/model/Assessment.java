package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A coordinator's judgement of how serious a reported disaster is. The
 * assessment records the severity, and the priority follows from it rather
 * than being entered separately, so the two can never disagree.
 */
public class Assessment {

    private String assessmentId;
    private Severity severity;
    private String notes;
    private LocalDateTime assessedAt;
    private String assessedBy;

    /**
     * Creates an assessment stamped with the current time.
     *
     * @param assessmentId unique identifier
     * @param severity     the severity judged
     * @param notes        optional notes, up to the permitted length
     * @param assessedBy   the username of the coordinator who assessed it
     */
    public Assessment(String assessmentId, Severity severity, String notes, String assessedBy) {
        this.assessmentId = assessmentId;
        if (severity == null) {
            throw new IllegalArgumentException("An assessment must record a severity.");
        }
        this.severity = severity;
        setNotes(notes);
        this.assessedBy = assessedBy;
        this.assessedAt = LocalDateTime.now();
    }

    /** No-argument constructor required by Gson. */
    protected Assessment() {
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getNotes() {
        return notes;
    }

    public final void setNotes(String notes) {
        if (notes != null && notes.length() > DrsConstants.ASSESSMENT_NOTES_MAX_LENGTH) {
            throw new IllegalArgumentException("Assessment notes may be at most "
                    + DrsConstants.ASSESSMENT_NOTES_MAX_LENGTH + " characters.");
        }
        this.notes = (notes == null || notes.trim().isEmpty()) ? null : notes.trim();
    }

    public LocalDateTime getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }

    public String getAssessedBy() {
        return assessedBy;
    }

    public void setAssessedBy(String assessedBy) {
        this.assessedBy = assessedBy;
    }

    /**
     * The response priority this assessment warrants, taken from the severity
     * recorded.
     *
     * @return the derived priority
     */
    public Priority derivePriority() {
        return severity.toPriority();
    }

    @Override
    public String toString() {
        return severity + " assessed " + assessedAt
                + (assessedBy == null ? "" : " by " + assessedBy)
                + ", priority " + derivePriority().name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Assessment)) {
            return false;
        }
        return Objects.equals(assessmentId, ((Assessment) o).assessmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assessmentId);
    }
}
