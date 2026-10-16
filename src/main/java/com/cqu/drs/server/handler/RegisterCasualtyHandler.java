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
 * Records a casualty at the scene.
 *
 * <h2>Why the name is optional in a payload that requires a triage category</h2>
 * At a scene people are recorded before anybody knows who they are. A handler
 * that required a name would mean unidentified casualties were not recorded at
 * all, which is the opposite of what a registry is for. The triage category is
 * required because it is the clinical judgement the record exists to carry.
 *
 * <p>The personal fields in this payload cross the socket readable, as every
 * other field does. The transport is not encrypted, which the report records
 * as a known limitation with TLS as its remedy; what this feature adds is that
 * the values do not then sit readable in the database, which is where they
 * stay for years rather than milliseconds.
 */
public final class RegisterCasualtyHandler implements OperationHandler {

    private final CasualtyOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public RegisterCasualtyHandler(CasualtyOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.CASUALTY_REGISTER;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        TriageCategory triage = Payloads.requireEnum(
                request.getPayload(), "triage", TriageCategory.class);

        String name = Payloads.optionalString(request.getPayload(), "name");
        String contactNumber =
                Payloads.optionalString(request.getPayload(), "contactNumber");
        String location = Payloads.optionalString(request.getPayload(), "location");
        String notes = Payloads.optionalString(request.getPayload(), "notes");

        Double age = Payloads.optionalDouble(request.getPayload(), "age");

        try {
            return Payloads.of("casualty", service.register(reference, name,
                    age == null ? null : (int) Math.rint(age), contactNumber, triage,
                    location, notes, session.getUsername()));

        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
