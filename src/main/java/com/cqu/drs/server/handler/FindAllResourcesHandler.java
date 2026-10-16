package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.ResourceOperations;
import com.google.gson.JsonObject;

/**
 * Every resource pool, with what is left in each.
 *
 * The counts are read at the moment of the request and are out of date the
 * instant they are sent, because another coordinator may allocate from a pool
 * while this reply is in flight. That is inherent and is why the allocation
 * itself is decided on the server with the row locked rather than from what a
 * screen is showing.
 */
public final class FindAllResourcesHandler implements OperationHandler {

    private final ResourceOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public FindAllResourcesHandler(ResourceOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.RESOURCE_FIND_ALL;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("resources", service.findAll());
    }
}
