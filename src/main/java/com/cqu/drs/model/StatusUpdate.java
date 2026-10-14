package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One entry in the history of a response task. A task keeps every update it
 * has received rather than only its current status, because FR17 requires a
 * timestamped history that can be reviewed afterwards.
 */
public class StatusUpdate {

    private String updateId;
    private ResponseStatus status;
    private String note;
    private LocalDateTime updatedAt;
    private String updatedBy;

    /**
     * Creates a status update stamped with the current time.
     *
     * @param updateId  unique identifier
     * @param status    the status being recorded
     * @param note      an optional note, up to the permitted length
     * @param updatedBy the username of the responder making the update
     */
    public StatusUpdate(String updateId, ResponseStatus status, String note, String updatedBy) {
        this.updateId = updateId;
        if (status == null) {
            throw new IllegalArgumentException("A status update must carry a status.");
        }
        this.status = status;
        setNote(note);
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /** No-argument constructor required by Gson. */
    protected StatusUpdate() {
    }

    public String getUpdateId() {
        return updateId;
    }

    public void setUpdateId(String updateId) {
        this.updateId = updateId;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public void setStatus(ResponseStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public final void setNote(String note) {
        if (note != null && note.length() > DrsConstants.STATUS_NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("A status note may be at most "
                    + DrsConstants.STATUS_NOTE_MAX_LENGTH + " characters.");
        }
        this.note = (note == null || note.trim().isEmpty()) ? null : note.trim();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public String toString() {
        return status.getLabel() + " at " + updatedAt
                + (updatedBy == null ? "" : " by " + updatedBy)
                + (note == null ? "" : ": " + note);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StatusUpdate)) {
            return false;
        }
        return Objects.equals(updateId, ((StatusUpdate) o).updateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(updateId);
    }
}
