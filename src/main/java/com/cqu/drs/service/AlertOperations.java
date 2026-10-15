package com.cqu.drs.service;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Severity;

import java.util.List;

/**
 * Issuing public warnings and evacuation instructions.
 */
public interface AlertOperations {

    /**
     * Issues an alert for a disaster and broadcasts it.
     *
     * Whether the broadcast reached anyone is recorded on the alert rather
     * than assumed, so a failed one can be noticed and followed up.
     *
     * @param reference    the disaster the alert is for
     * @param type         warning or evacuation
     * @param affectedArea the area it applies to
     * @param message      the text sent
     * @param issuedBy     the coordinator's username
     * @return the alert, carrying whether it was delivered
     */
    Alert issue(String reference, AlertType type, String affectedArea,
                String message, String issuedBy);

    /**
     * Whether a severity is serious enough to offer an evacuation.
     *
     * Answered without contacting the server: it is a rule about a value the
     * screen already holds.
     *
     * @param severity the severity assessed
     * @return true when an evacuation should be offered
     */
    boolean warrantsEvacuation(Severity severity);

    /**
     * Every alert issued.
     *
     * @return the alerts
     */
    List<Alert> findAll();
}
