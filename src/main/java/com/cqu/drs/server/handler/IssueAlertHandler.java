package com.cqu.drs.server.handler;

import com.cqu.drs.model.AlertType;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AlertOperations;
import com.google.gson.JsonObject;

/**
 * Issues a warning or an evacuation instruction for a disaster.
 *
 * The alert records whether the broadcast reached its recipients, and that
 * answer is returned rather than assumed, so a coordinator sees that a
 * warning failed to go out instead of believing it did.
 */
public final class IssueAlertHandler implements OperationHandler {

    private final AlertOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public IssueAlertHandler(AlertOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ALERT_ISSUE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        AlertType type = Payloads.requireEnum(request.getPayload(), "type", AlertType.class);
        String affectedArea = Payloads.requireString(request.getPayload(), "affectedArea");
        String message = Payloads.requireString(request.getPayload(), "message");

        try {
            // The issuer is the session's username, never a name taken from
            // the payload. An alert is a public statement and the record of
            // who made it has to be the server's, not the client's.
            return Payloads.of("alert", service.issue(reference, type, affectedArea,
                    message, session.getUsername()));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("report", reference);
        }
    }
}
