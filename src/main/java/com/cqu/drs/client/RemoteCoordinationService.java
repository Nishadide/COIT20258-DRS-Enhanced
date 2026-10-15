package com.cqu.drs.client;

import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.StatusUpdate;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.CoordinationOperations;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * Dispatches work and follows it, by asking the server.
 *
 * <h2>Identifiers are sent, not objects</h2>
 * {@link #allocate} sends the department identifiers the coordinator chose
 * rather than the Department objects the screen is holding. A client that sent
 * whole departments could claim one was available when it was not, or invent a
 * department that does not exist, and the server would have nothing to check
 * it against. Sending identifiers means the server looks each one up in its
 * own data, which is the only copy whose state can be trusted.
 *
 * <p>The whole selection goes in one request, as it did in one call in
 * Assessment 2. Dispatching to four departments is one operation and stays one
 * operation, rather than becoming four requests that can half succeed.
 */
public final class RemoteCoordinationService implements CoordinationOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteCoordinationService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public List<ResponseTask> allocate(String reference, List<Department> departments,
                                       String dispatchedBy) {
        if (departments == null || departments.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one department.");
        }

        JsonArray ids = new JsonArray();
        for (Department department : departments) {
            if (department != null) {
                ids.add(department.getDepartmentId());
            }
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);
        payload.add("departmentIds", ids);

        JsonObject result = client.sendOrThrow(Operation.COORDINATION_ALLOCATE, payload);
        return Results.list(result, "tasks", ResponseTask.class);
    }

    @Override
    public StatusUpdate updateStatus(String reference, String taskId,
                                     ResponseStatus status, String note,
                                     String updatedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);
        payload.addProperty("taskId", taskId);
        payload.addProperty("status", status == null ? null : status.name());
        if (note != null && !note.isBlank()) {
            payload.addProperty("note", note);
        }

        JsonObject result =
                client.sendOrThrow(Operation.COORDINATION_UPDATE_STATUS, payload);
        return Results.one(result, "statusUpdate", StatusUpdate.class);
    }

    @Override
    public List<ResponseTask> findTasksForDepartment(String departmentId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("departmentId", departmentId);

        JsonObject result = client.sendOrThrow(
                Operation.COORDINATION_TASKS_FOR_DEPARTMENT, payload);
        return Results.list(result, "tasks", ResponseTask.class);
    }

    @Override
    public Optional<DisasterReport> findReportForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("taskId", taskId);

        return client.sendAllowingNotFound(Operation.COORDINATION_REPORT_FOR_TASK, payload)
                .flatMap(result -> Results.optional(result, "report", DisasterReport.class));
    }

    @Override
    public List<Department> availableDepartments() {
        JsonObject result = client.sendOrThrow(
                Operation.COORDINATION_AVAILABLE_DEPARTMENTS, new JsonObject());
        return Results.list(result, "departments", Department.class);
    }

}
