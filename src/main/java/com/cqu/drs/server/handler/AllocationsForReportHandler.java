package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.ResourceOperations;
import com.google.gson.JsonObject;

/**
 * What has been committed to one disaster, held and released alike.
 *
 * The released ones are included deliberately. A coordinator taking over a
 * response needs to see that two crews were committed and stood down an hour
 * ago, not a list that silently omits them.
 */
public final class AllocationsForReportHandler implements OperationHandler {

    private final ResourceOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public AllocationsForReportHandler(ResourceOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.RESOURCE_ALLOCATIONS_FOR_REPORT;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");

        try {
            return Payloads.of("allocations",
                    service.findAllocationsForReport(reference));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
