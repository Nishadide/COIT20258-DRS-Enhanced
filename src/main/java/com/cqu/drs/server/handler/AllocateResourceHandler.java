package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.ResourceOperations;
import com.google.gson.JsonObject;

/**
 * Commits units of a resource to a disaster.
 *
 * <h2>This is the operation the multi-threaded server exists to make
 * interesting</h2>
 * Two coordinators, each on their own client, each with their own thread on
 * the server, can send this for the same pool at the same moment. Neither
 * thread decides anything: both ask the repository, which asks the database to
 * order them. One is given the units and the other is told what is actually
 * left.
 *
 * <p>The refusal comes back as CONFLICT rather than VALIDATION_FAILED, and the
 * difference matters to whoever reads it. Nothing was wrong with the request;
 * it was a good request that lost a race, and the right response on the screen
 * is to show what is left and let the coordinator decide again, not to ask
 * them to correct an input.
 */
public final class AllocateResourceHandler implements OperationHandler {

    private final ResourceOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public AllocateResourceHandler(ResourceOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.RESOURCE_ALLOCATE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String resourceId = Payloads.requireString(request.getPayload(), "resourceId");
        String reference = Payloads.requireString(request.getPayload(), "reference");
        int units = Payloads.requireInt(request.getPayload(), "units");

        try {
            // The coordinator is the one on the session. A client cannot
            // allocate resources in somebody else's name.
            return Payloads.of("allocation", service.allocate(resourceId, reference,
                    units, session.getUsername()));

        } catch (IllegalStateException shortfall) {
            throw new ProtocolException(ErrorCode.CONFLICT, shortfall.getMessage(),
                    shortfall);

        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
