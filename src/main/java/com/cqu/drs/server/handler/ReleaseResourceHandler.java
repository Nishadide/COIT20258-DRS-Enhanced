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
 * Returns an allocation's units to its pool.
 *
 * Releasing twice is refused rather than ignored. Two clicks on a slow screen,
 * or two coordinators both tidying up after the same response, would otherwise
 * put the units back twice and leave a pool claiming more crews than the
 * department owns. The second release is reported as a conflict, which says
 * what happened: the work was already done.
 */
public final class ReleaseResourceHandler implements OperationHandler {

    private final ResourceOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public ReleaseResourceHandler(ResourceOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.RESOURCE_RELEASE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String allocationId = Payloads.requireString(request.getPayload(), "allocationId");

        try {
            return Payloads.of("allocation",
                    service.release(allocationId, session.getUsername()));

        } catch (IllegalStateException alreadyReleased) {
            throw new ProtocolException(ErrorCode.CONFLICT,
                    alreadyReleased.getMessage(), alreadyReleased);

        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("allocation", allocationId);
        }
    }
}
