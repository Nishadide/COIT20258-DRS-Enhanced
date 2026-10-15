package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.CoordinationOperations;
import com.cqu.drs.service.DepartmentOperations;
import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches one disaster to several departments at once.
 *
 * The whole selection arrives in a single request, so the batch dispatch built
 * in Assessment 2 stays one action rather than becoming one request per
 * department, and either every task is created or none is.
 */
public final class AllocateHandler implements OperationHandler {

    private final CoordinationOperations service;
    private final DepartmentOperations departments;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     * @param departments the departments this handler works through
     */
    public AllocateHandler(CoordinationOperations service, DepartmentOperations departments) {
        if (service == null || departments == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
        this.departments = departments;
    }

    @Override
    public Operation operation() {
        return Operation.COORDINATION_ALLOCATE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        List<String> ids = Payloads.requireStrings(request.getPayload(), "departmentIds");

        // The client sends identifiers; the departments themselves are looked
        // up here. A client that sent whole Department objects could claim a
        // department was available when it was not, or invent one.
        List<Department> chosen = new ArrayList<>(ids.size());
        for (String id : ids) {
            chosen.add(departments.findById(id).orElseThrow(
                    () -> ProtocolException.notFound("department", id)));
        }

        try {
            List<ResponseTask> created =
                    service.allocate(reference, chosen, session.getUsername());

            JsonArray tasks = new JsonArray();
            for (ResponseTask task : created) {
                tasks.add(ProtocolCodec.gson().toJsonTree(task));
            }
            JsonObject result = new JsonObject();
            result.add("tasks", tasks);
            return result;

        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        } catch (IllegalStateException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
