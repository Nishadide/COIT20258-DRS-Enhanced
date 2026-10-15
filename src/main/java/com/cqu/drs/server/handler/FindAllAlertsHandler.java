package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AlertOperations;
import com.google.gson.JsonObject;

/**
 * Every alert that has been issued.
 */
public final class FindAllAlertsHandler implements OperationHandler {

    private final AlertOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public FindAllAlertsHandler(AlertOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ALERT_FIND_ALL;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("alerts", service.findAll());
    }
}
