package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.service.AssessmentOperations;

/**
 * Assessed disasters in the order they should be dealt with.
 */
public final class QueueByPriorityHandler implements OperationHandler {

    private final AssessmentOperations service;

    /**
     * Creates the handler.
     *
     * @param service reads the queue
     */
    public QueueByPriorityHandler(AssessmentOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ASSESSMENT_QUEUE_BY_PRIORITY;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("reports", service.queueByPriority());
    }
}
