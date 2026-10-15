package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.service.ReportOperations;

/**
 * Every disaster report, for the coordinator's dashboard.
 */
public final class FindAllReportsHandler implements OperationHandler {

    private final ReportOperations service;

    /**
     * Creates the handler.
     *
     * @param service reads the reports
     */
    public FindAllReportsHandler(ReportOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.REPORT_FIND_ALL;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("reports", service.findAll());
    }
}
