package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.ResourceOperations;
import com.google.gson.JsonObject;

/**
 * The pools one department holds.
 *
 * A responder may ask only about their own department. The role check the
 * dispatcher already made says a responder may use this operation at all; it
 * cannot say which department they may ask about, because two responders hold
 * the same role and belong to different departments. That is decided here,
 * from the department recorded on the session at sign-in, which no client can
 * alter.
 */
public final class ResourcesForDepartmentHandler implements OperationHandler {

    private final ResourceOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public ResourcesForDepartmentHandler(ResourceOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.RESOURCE_FIND_BY_DEPARTMENT;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String departmentId =
                Payloads.requireString(request.getPayload(), "departmentId");

        if (!session.mayActOnDepartment(departmentId)) {
            throw ProtocolException.accessDenied(
                    "You may only see the resources held by your own department.");
        }

        try {
            return Payloads.of("resources", service.findByDepartment(departmentId));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
