package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.AssessmentOperations;

/**
 * Closes a disaster once every dispatched task has finished.
 */
public final class ResolveReportHandler implements OperationHandler {

    private final AssessmentOperations service;

    /**
     * Creates the handler.
     *
     * @param service closes the report
     */
    public ResolveReportHandler(AssessmentOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ASSESSMENT_RESOLVE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        try {
            service.resolve(reference, session.getUsername());
            return Payloads.acknowledged();
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("disaster report", reference);
        } catch (IllegalStateException e) {
            // Refused because work is still open, which is a rule rather than
            // a fault, and the coordinator needs to be told which.
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
