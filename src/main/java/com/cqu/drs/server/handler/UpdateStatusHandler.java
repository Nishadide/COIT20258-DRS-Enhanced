package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.CoordinationOperations;

/**
 * Records progress on a dispatched task.
 */
public final class UpdateStatusHandler implements OperationHandler {

    private final CoordinationOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public UpdateStatusHandler(CoordinationOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.COORDINATION_UPDATE_STATUS;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");
        String taskId = Payloads.requireString(request.getPayload(), "taskId");
        ResponseStatus status = Payloads.requireEnum(
                request.getPayload(), "status", ResponseStatus.class);
        String note = Payloads.optionalString(request.getPayload(), "note");

        requireTaskBelongsToSession(taskId, session);

        try {
            return Payloads.of("statusUpdate", service.updateStatus(
                    reference, taskId, status, note, session.getUsername()));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("response task", taskId);
        } catch (IllegalStateException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }

    /**
     * Stops a responder updating another department's work.
     *
     * The role check the dispatcher already made says a responder may use this
     * operation. It cannot say which records they may use it on, because it
     * does not know which records a request will touch. Two users holding the
     * same role can be entitled to different rows, and this is where that is
     * decided.
     *
     * The refusal says nothing about whether the task exists, so it cannot be
     * used to find out what other departments are working on.
     *
     * @param taskId  the task being updated
     * @param session the session the request arrived on
     */
    private void requireTaskBelongsToSession(String taskId, Session session) {
        if (session.getRole() != UserRole.RESPONDER) {
            return;
        }
        boolean theirs = service.findTasksForDepartment(session.getDepartmentId())
                .stream()
                .map(ResponseTask::getTaskId)
                .anyMatch(taskId::equals);

        if (!theirs) {
            throw ProtocolException.accessDenied(
                    "You may only update work dispatched to your own department.");
        }
    }
}
