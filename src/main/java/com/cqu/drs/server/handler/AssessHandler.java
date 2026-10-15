package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.Severity;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.AssessmentOperations;

/**
 * Records a coordinator's judgement of how serious a disaster is.
 */
public final class AssessHandler implements OperationHandler {

    private final AssessmentOperations service;

    /**
     * Creates the handler.
     *
     * @param service records the assessment
     */
    public AssessHandler(AssessmentOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ASSESSMENT_ASSESS;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        Severity severity = Payloads.requireEnum(
                request.getPayload(), "severity", Severity.class);
        String notes = Payloads.optionalString(request.getPayload(), "notes");

        try {
            // Recorded against the session's user, not against a name the
            // request supplies, so an assessment cannot be attributed to
            // somebody else.
            Assessment assessment =
                    service.assess(reference, severity, notes, session.getUsername());

            JsonObject result = new JsonObject();
            result.add("assessment", ProtocolCodec.gson().toJsonTree(assessment));
            result.addProperty("priority", service.prioritise(assessment).name());
            return result;

        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("disaster report", reference);
        } catch (IllegalStateException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
