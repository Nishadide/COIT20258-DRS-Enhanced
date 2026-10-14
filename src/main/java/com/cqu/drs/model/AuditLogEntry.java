package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One record in the audit log. Entries are appended and never altered, so that
 * the actions taken during a response can be reviewed afterwards.
 */
public class AuditLogEntry {

    private String entryId;
    private String actor;
    private String action;
    private LocalDateTime timestamp;

    /**
     * Creates an audit entry stamped with the current time.
     *
     * @param entryId unique identifier
     * @param actor   who performed the action, or "anonymous"
     * @param action  what was done
     */
    public AuditLogEntry(String entryId, String actor, String action) {
        this.entryId = entryId;
        this.actor = (actor == null || actor.trim().isEmpty()) ? "anonymous" : actor.trim();
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("An audit entry must describe an action.");
        }
        this.action = action.trim();
        this.timestamp = LocalDateTime.now();
    }

    /** No-argument constructor required by Gson. */
    protected AuditLogEntry() {
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return timestamp + "  " + actor + "  " + action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditLogEntry)) {
            return false;
        }
        return Objects.equals(entryId, ((AuditLogEntry) o).entryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryId);
    }
}
