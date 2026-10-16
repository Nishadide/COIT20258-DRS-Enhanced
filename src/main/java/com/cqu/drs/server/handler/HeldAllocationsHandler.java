package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.ResourceOperations;
import com.google.gson.JsonObject;

/**
 * Everything currently committed, across every disaster.
 *
 * This is the answer to the question a coordinator asks when a pool is empty
 * and they need to know where its units went and whether any can be stood
 * down.
 */
public final class HeldAllocationsHandler implements OperationHandler {

    private final ResourceOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public HeldAllocationsHandler(ResourceOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.RESOURCE_HELD;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("allocations", service.findHeldAllocations());
    }
}
