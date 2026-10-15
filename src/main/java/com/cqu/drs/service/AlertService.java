package com.cqu.drs.service;

import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.Severity;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.DisasterReportRepository;

import java.util.List;
import java.util.UUID;

/**
 * Issues public warnings and evacuation instructions. Whether an alert reached
 * its recipients is recorded on the alert rather than assumed, so that a
 * failed broadcast can be seen and dealt with.
 */
public class AlertService implements AlertOperations {

    private final DisasterReportRepository reportRepository;
    private final AlertRepository alertRepository;
    private final AuditService auditService;

    /** Set false to simulate a broadcast channel that cannot be reached. */
    private boolean channelReachable = true;

    /**
     * Creates the service.
     *
     * @param reportRepository where reports are stored
     * @param alertRepository  where alerts are stored
     * @param auditService     used to record alerts issued
     */
    public AlertService(DisasterReportRepository reportRepository,
                        AlertRepository alertRepository,
                        AuditService auditService) {
        this.reportRepository = reportRepository;
        this.alertRepository = alertRepository;
        this.auditService = auditService;
    }

    /**
     * Issues an alert for a disaster and attempts to broadcast it.
     *
     * @param reference    the report the alert concerns
     * @param type         warning or evacuation
     * @param affectedArea the area the alert applies to
     * @param message      the text sent to recipients
     * @param issuedBy     the username of the coordinator
     * @return the alert issued, carrying whether it was delivered
     * @throws IllegalArgumentException when the report cannot be found
     */
    @Override
    public Alert issue(String reference, AlertType type, String affectedArea,
                       String message, String issuedBy) {
        DisasterReport report = reportRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No report holds the reference " + reference + "."));

        Alert alert = new Alert(DrsConstants.ALERT_PREFIX + UUID.randomUUID().toString()
                .substring(0, DrsConstants.SHORT_ID_LENGTH),
                type, affectedArea, message, issuedBy);
        boolean delivered = broadcast(alert);

        report.addAlert(alert);
        reportRepository.save(report);
        alertRepository.save(alert);

        auditService.record(issuedBy, "Issued " + type.getLabel() + " for "
                + reference + " covering " + affectedArea
                + (delivered ? "" : "; broadcast failed"));
        return alert;
    }

    /**
     * Sends an alert to its recipients. The channel is simulated in this
     * prototype, so the outcome follows from whether the channel is currently
     * reachable, and the result is recorded on the alert.
     *
     * @param alert the alert to send
     * @return true when the alert was delivered
     */
    public boolean broadcast(Alert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("Cannot broadcast a null alert.");
        }
        return alert.broadcast(channelReachable);
    }

    /**
     * Reports whether a severity is serious enough to warrant considering an
     * evacuation, which is the condition under which the coordinator is
     * prompted after an assessment.
     *
     * @param severity the severity assessed
     * @return true when an evacuation alert should be offered
     */
    @Override
    public boolean warrantsEvacuation(Severity severity) {
        // Delegated so that the client, which answers this without contacting
        // the server, and the server cannot come to disagree about it.
        return AlertRules.warrantsEvacuation(severity);
    }

    /**
     * Every alert issued.
     *
     * @return the alerts
     */
    @Override
    public List<Alert> findAll() {
        return alertRepository.findAll();
    }

    /**
     * Sets whether the simulated broadcast channel can be reached. Used to
     * exercise the failure path.
     *
     * @param channelReachable whether the channel is available
     */
    public void setChannelReachable(boolean channelReachable) {
        this.channelReachable = channelReachable;
    }

    /**
     * Whether the simulated broadcast channel can be reached.
     *
     * @return true when the channel is available
     */
    public boolean isChannelReachable() {
        return channelReachable;
    }
}
