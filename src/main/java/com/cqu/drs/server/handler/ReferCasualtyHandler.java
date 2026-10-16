package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.CasualtyOperations;
import com.google.gson.JsonObject;

/**
 * Records that a casualty has been sent to a hospital.
 *
 * Referring a casualty recorded as deceased is refused, and refused as a
 * validation failure rather than as a not found: the record exists, and the
 * message says what to do if the category was recorded in error. Allowing it
 * would put a transport request into a hospital's queue ahead of living
 * patients.
 */
public final class ReferCasualtyHandler implements OperationHandler {

    private final CasualtyOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public ReferCasualtyHandler(CasualtyOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.CASUALTY_REFER;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String casualtyId = Payloads.requireString(request.getPayload(), "casualtyId");
        String hospital = Payloads.requireString(request.getPayload(), "hospital");

        try {
            return Payloads.of("casualty",
                    service.refer(casualtyId, hospital, session.getUsername()));

        } catch (IllegalStateException deceased) {
            throw ProtocolException.validation(deceased.getMessage());

        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("casualty record", casualtyId);
        }
    }
}
