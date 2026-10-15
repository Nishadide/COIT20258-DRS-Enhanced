package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.DepartmentOperations;

/**
 * Marks a department available or unavailable.
 */
public final class SetDepartmentAvailabilityHandler implements OperationHandler {

    private final DepartmentOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public SetDepartmentAvailabilityHandler(DepartmentOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.DEPARTMENT_SET_AVAILABILITY;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String departmentId =
                Payloads.requireString(request.getPayload(), "departmentId");
        boolean available = Payloads.requireBoolean(request.getPayload(), "available");

        try {
            // Saving the department and recording who changed it happen
            // together, inside the service. In DRS-Initial the screen did them
            // one after the other, which meant they could come apart, and in
            // three tiers the audit entry cannot be written by a client at all.
            return Payloads.of("department",
                    service.setAvailability(departmentId, available,
                            session.getUsername()));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("department", departmentId);
        }
    }
}
