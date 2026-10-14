package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A public warning or evacuation instruction issued for a disaster. Whether
 * the alert actually reached its recipients is recorded rather than assumed,
 * so that a failed broadcast can be detected and followed up.
 */
public class Alert {

    private String alertId;
    private AlertType type;
    private String affectedArea;
    private String message;
    private LocalDateTime issuedAt;
    private boolean delivered;
    private String issuedBy;

    /**
     * Creates an alert stamped with the current time. The alert is not
     * delivered until broadcast is called.
     *
     * @param alertId      unique identifier
     * @param type         warning or evacuation
     * @param affectedArea the area the alert applies to
     * @param message      the text sent to recipients
     * @param issuedBy     the username of the coordinator issuing it
     */
    public Alert(String alertId, AlertType type, String affectedArea,
                 String message, String issuedBy) {
        this.alertId = alertId;
        if (type == null) {
            throw new IllegalArgumentException("An alert must have a type.");
        }
        this.type = type;
        setAffectedArea(affectedArea);
        setMessage(message);
        this.issuedBy = issuedBy;
        this.issuedAt = LocalDateTime.now();
        this.delivered = false;
    }

    /** No-argument constructor required by Gson. */
    protected Alert() {
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public AlertType getType() {
        return type;
    }

    public void setType(AlertType type) {
        this.type = type;
    }

    public String getAffectedArea() {
        return affectedArea;
    }

    public final void setAffectedArea(String affectedArea) {
        String trimmed = affectedArea == null ? "" : affectedArea.trim();
        if (trimmed.length() < DrsConstants.AFFECTED_AREA_MIN_LENGTH
                || trimmed.length() > DrsConstants.AFFECTED_AREA_MAX_LENGTH) {
            throw new IllegalArgumentException("Affected area must be between "
                    + DrsConstants.AFFECTED_AREA_MIN_LENGTH + " and "
                    + DrsConstants.AFFECTED_AREA_MAX_LENGTH + " characters.");
        }
        this.affectedArea = trimmed;
    }

    public String getMessage() {
        return message;
    }

    public final void setMessage(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.length() < DrsConstants.ALERT_MESSAGE_MIN_LENGTH
                || trimmed.length() > DrsConstants.ALERT_MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("Alert message must be between "
                    + DrsConstants.ALERT_MESSAGE_MIN_LENGTH + " and "
                    + DrsConstants.ALERT_MESSAGE_MAX_LENGTH + " characters.");
        }
        this.message = trimmed;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    /**
     * Sends this alert to its recipients. In this prototype the channel is
     * simulated, so the outcome is decided by whether a channel is reachable
     * and the result is recorded on the alert.
     *
     * @param channelReachable whether the broadcast channel could be reached
     * @return true when the alert was delivered
     */
    public boolean broadcast(boolean channelReachable) {
        this.delivered = channelReachable;
        return this.delivered;
    }

    @Override
    public String toString() {
        return type.getLabel() + " for " + affectedArea + " issued " + issuedAt
                + (delivered ? " [delivered]" : " [not delivered]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Alert)) {
            return false;
        }
        return Objects.equals(alertId, ((Alert) o).alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId);
    }
}
