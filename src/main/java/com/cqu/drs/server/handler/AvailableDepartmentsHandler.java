package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.service.CoordinationOperations;

/**
 * The departments currently able to take work.
 */
public final class AvailableDepartmentsHandler implements OperationHandler {

    private final CoordinationOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public AvailableDepartmentsHandler(CoordinationOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.COORDINATION_AVAILABLE_DEPARTMENTS;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("departments", service.availableDepartments());
    }
}
