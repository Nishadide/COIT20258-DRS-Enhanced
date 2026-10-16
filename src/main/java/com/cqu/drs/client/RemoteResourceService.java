package com.cqu.drs.client;

import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.service.ResourceOperations;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Asks the server to commit and release resources.
 *
 * <h2>Nothing here decides whether the units are available</h2>
 * The screen knows what the pools looked like when it last asked, and that is
 * all it knows. It may grey out a control that cannot possibly succeed, but it
 * never concludes that an allocation will succeed, because between the moment
 * it drew the number and the moment the coordinator clicks, another
 * coordinator on another machine may have taken the units.
 *
 * <p>So this sends the request and reports what came back. A refusal is an
 * ordinary answer that the screen shows along with the number actually left,
 * not an error to be hidden.
 */
public final class RemoteResourceService implements ResourceOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteResourceService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public List<Resource> findAll() {
        JsonObject result =
                client.sendOrThrow(Operation.RESOURCE_FIND_ALL, new JsonObject());
        return Results.list(result, "resources", Resource.class);
    }

    @Override
    public List<Resource> findByDepartment(String departmentId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("departmentId", departmentId);

        JsonObject result =
                client.sendOrThrow(Operation.RESOURCE_FIND_BY_DEPARTMENT, payload);
        return Results.list(result, "resources", Resource.class);
    }

    /**
     * {@inheritDoc}
     *
     * The coordinator's name is not sent. The server records the allocation
     * against the user on the session, so a modified client cannot commit
     * resources in another coordinator's name.
     */
    @Override
    public ResourceAllocation allocate(String resourceId, String reference, int units,
                                       String requestedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("resourceId", resourceId);
        payload.addProperty("reference", reference);
        payload.addProperty("units", units);

        JsonObject result = client.sendOrThrow(Operation.RESOURCE_ALLOCATE, payload);
        return Results.one(result, "allocation", ResourceAllocation.class);
    }

    @Override
    public ResourceAllocation release(String allocationId, String releasedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("allocationId", allocationId);

        JsonObject result = client.sendOrThrow(Operation.RESOURCE_RELEASE, payload);
        return Results.one(result, "allocation", ResourceAllocation.class);
    }

    @Override
    public List<ResourceAllocation> findAllocationsForReport(String reference) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        JsonObject result = client.sendOrThrow(
                Operation.RESOURCE_ALLOCATIONS_FOR_REPORT, payload);
        return Results.list(result, "allocations", ResourceAllocation.class);
    }

    @Override
    public List<ResourceAllocation> findHeldAllocations() {
        JsonObject result =
                client.sendOrThrow(Operation.RESOURCE_HELD, new JsonObject());
        return Results.list(result, "allocations", ResourceAllocation.class);
    }
}
