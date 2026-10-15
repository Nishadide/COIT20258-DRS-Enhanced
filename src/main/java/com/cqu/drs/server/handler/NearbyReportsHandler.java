package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.service.ReportOperations;

/**
 * Other reports close to a given one, which is how a coordinator sees that
 * several calls describe the same incident.
 */
public final class NearbyReportsHandler implements OperationHandler {

    private final ReportOperations service;

    /**
     * Creates the handler.
     *
     * @param service finds the nearby reports
     */
    public NearbyReportsHandler(ReportOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.REPORT_NEARBY;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        return Payloads.of("reports", service.nearbyReports(reference));
    }
}
