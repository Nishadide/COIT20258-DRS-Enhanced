package com.cqu.drs.server.handler;

import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.CasualtyOperations;
import com.google.gson.JsonObject;

/**
 * Records a change in where a casualty is in the response: treated on site,
 * discharged, or back to registered if a change was recorded in error.
 *
 * A referral is deliberately not reachable through here. It has its own
 * operation because it must carry the hospital with it, and a status set to
 * REFERRED with no hospital recorded would be a record saying somebody was
 * sent somewhere nobody can name. The service refuses it, and so does the
 * database, which will not accept a referred row with no destination.
 */
public final class UpdateCasualtyStatusHandler implements OperationHandler {

    private final CasualtyOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public UpdateCasualtyStatusHandler(CasualtyOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.CASUALTY_STATUS;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String casualtyId = Payloads.requireString(request.getPayload(), "casualtyId");
        CasualtyStatus status = Payloads.requireEnum(
                request.getPayload(), "status", CasualtyStatus.class);

        try {
            return Payloads.of("casualty",
                    service.updateStatus(casualtyId, status, session.getUsername()));

        } catch (IllegalArgumentException e) {
            // Either the record is unknown or the status was REFERRED without
            // a hospital. The message says which, and neither is a fault in
            // the application.
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
