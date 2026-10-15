package com.cqu.drs.client;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Severity;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.AlertOperations;
import com.cqu.drs.service.AlertRules;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Issues public warnings by asking the server, which owns the broadcast.
 *
 * The alert that comes back carries whether the broadcast reached its
 * recipients. That answer is the server's, because the server is what did the
 * sending; a client reporting its own success would be reporting on something
 * it did not do.
 */
public final class RemoteAlertService implements AlertOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteAlertService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public Alert issue(String reference, AlertType type, String affectedArea,
                       String message, String issuedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);
        payload.addProperty("type", type == null ? null : type.name());
        payload.addProperty("affectedArea", affectedArea);
        payload.addProperty("message", message);

        JsonObject result = client.sendOrThrow(Operation.ALERT_ISSUE, payload);
        return Results.one(result, "alert", Alert.class);
    }

    /**
     * {@inheritDoc}
     *
     * Answered without contacting the server, through the same rule class the
     * server uses, so the prompt appears the instant an assessment is recorded
     * and the two tiers cannot disagree about when an evacuation is warranted.
     */
    @Override
    public boolean warrantsEvacuation(Severity severity) {
        return AlertRules.warrantsEvacuation(severity);
    }

    @Override
    public List<Alert> findAll() {
        JsonObject result = client.sendOrThrow(Operation.ALERT_FIND_ALL, new JsonObject());
        return Results.list(result, "alerts", Alert.class);
    }
}
