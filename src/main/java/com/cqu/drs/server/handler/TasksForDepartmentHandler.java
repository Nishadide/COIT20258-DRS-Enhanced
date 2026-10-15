package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.CoordinationOperations;

/**
 * The work dispatched to one department.
 */
public final class TasksForDepartmentHandler implements OperationHandler {

    private final CoordinationOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public TasksForDepartmentHandler(CoordinationOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.COORDINATION_TASKS_FOR_DEPARTMENT;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String requested = Payloads.requireString(request.getPayload(), "departmentId");

        // A responder may ask only about their own department. Taking the
        // department from the session rather than refusing outright would
        // quietly answer a different question than the one asked, so this
        // refuses and says why.
        if (session.getRole() == UserRole.RESPONDER
                && !requested.equals(session.getDepartmentId())) {
            throw ProtocolException.accessDenied(
                    "You may only see work dispatched to your own department.");
        }

        return Payloads.of("tasks", service.findTasksForDepartment(requested));
    }
}
