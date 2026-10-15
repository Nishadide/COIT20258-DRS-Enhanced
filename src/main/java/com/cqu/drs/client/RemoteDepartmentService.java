package com.cqu.drs.client;

import com.cqu.drs.model.Department;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.DepartmentOperations;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * The departments, read from the server, and availability changed through it.
 *
 * <h2>What this replaces</h2>
 * In DRS-Initial two screens held the department repository itself. The
 * account management screen fetched a department, flipped its availability,
 * saved it and then wrote an audit entry; the coordination screen read the
 * list straight out of the repository.
 *
 * <p>Neither can survive three tiers. A client has no repository to hold, and
 * the audit entry must be written by the server, which is the only party that
 * can vouch for what happened. The three steps became one operation, which is
 * also better than what it replaced: saving the change and recording it can no
 * longer come apart, as they could when a screen did them in sequence and
 * anything might happen in between.
 */
public final class RemoteDepartmentService implements DepartmentOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteDepartmentService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public List<Department> findAll() {
        JsonObject result =
                client.sendOrThrow(Operation.DEPARTMENT_FIND_ALL, new JsonObject());
        return Results.list(result, "departments", Department.class);
    }

    @Override
    public List<Department> findAvailable() {
        JsonObject result = client.sendOrThrow(
                Operation.COORDINATION_AVAILABLE_DEPARTMENTS, new JsonObject());
        return Results.list(result, "departments", Department.class);
    }

    /**
     * {@inheritDoc}
     *
     * Answered from the list rather than by an operation of its own, because
     * the protocol defines none and the list is small and already permitted to
     * whoever may ask this.
     */
    @Override
    public Optional<Department> findById(String departmentId) {
        if (departmentId == null || departmentId.isBlank()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(department -> departmentId.equals(department.getDepartmentId()))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     *
     * The department that comes back is the server's record of it, not the
     * object the screen was holding with a field flipped. The screen shows
     * what was actually stored, so a change that did not happen cannot be
     * displayed as though it had.
     */
    @Override
    public Department setAvailability(String departmentId, boolean available,
                                      String changedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("departmentId", departmentId);
        payload.addProperty("available", available);

        JsonObject result =
                client.sendOrThrow(Operation.DEPARTMENT_SET_AVAILABILITY, payload);
        return Results.one(result, "department", Department.class);
    }
}
