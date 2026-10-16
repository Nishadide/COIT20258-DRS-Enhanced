package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.CasualtyOperations;
import com.google.gson.JsonObject;

/**
 * Everyone recorded against one disaster, most urgent first.
 *
 * This is the operation that returns personal data, so it is the one the
 * access rules matter most for. Coordinators and responders may ask it because
 * they are the people running the response; an administrator may not, and the
 * protocol gives them no operation that would let them. They can ask for the
 * counts instead.
 */
public final class CasualtiesForReportHandler implements OperationHandler {

    private final CasualtyOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public CasualtiesForReportHandler(CasualtyOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.CASUALTY_FIND_FOR_REPORT;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");

        try {
            return Payloads.of("casualties", service.findForReport(reference));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
