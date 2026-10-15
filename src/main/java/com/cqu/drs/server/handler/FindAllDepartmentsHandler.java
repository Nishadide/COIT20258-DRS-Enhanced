package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.service.DepartmentOperations;

/**
 * Every department, available or not.
 */
public final class FindAllDepartmentsHandler implements OperationHandler {

    private final DepartmentOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public FindAllDepartmentsHandler(DepartmentOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.DEPARTMENT_FIND_ALL;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("departments", service.findAll());
    }
}
