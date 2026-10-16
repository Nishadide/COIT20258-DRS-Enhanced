package com.cqu.drs.server.handler;

import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.CasualtyOperations;
import com.google.gson.JsonObject;

/**
 * Changes a casualty's triage category as their condition changes.
 *
 * Who made the judgement is taken from the session, never from the payload. A
 * triage decision is a clinical one that somebody is accountable for, and a
 * record of it that a client could attribute to another responder would be
 * worth nothing in a review.
 */
public final class RetriageCasualtyHandler implements OperationHandler {

    private final CasualtyOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public RetriageCasualtyHandler(CasualtyOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.CASUALTY_TRIAGE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String casualtyId = Payloads.requireString(request.getPayload(), "casualtyId");
        TriageCategory triage = Payloads.requireEnum(
                request.getPayload(), "triage", TriageCategory.class);

        try {
            return Payloads.of("casualty",
                    service.retriage(casualtyId, triage, session.getUsername()));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("casualty record", casualtyId);
        }
    }
}
