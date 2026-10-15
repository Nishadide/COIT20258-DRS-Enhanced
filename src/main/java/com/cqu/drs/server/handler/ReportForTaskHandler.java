package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.CoordinationOperations;

import java.util.Optional;

/**
 * The disaster a dispatched task belongs to, so a responder can see what they
 * have been sent to.
 */
public final class ReportForTaskHandler implements OperationHandler {

    private final CoordinationOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public ReportForTaskHandler(CoordinationOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.COORDINATION_REPORT_FOR_TASK;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String taskId = Payloads.requireString(request.getPayload(), "taskId");

        if (session.getRole() == UserRole.RESPONDER) {
            boolean theirs = service.findTasksForDepartment(session.getDepartmentId())
                    .stream()
                    .map(ResponseTask::getTaskId)
                    .anyMatch(taskId::equals);
            if (!theirs) {
                throw ProtocolException.accessDenied(
                        "You may only see work dispatched to your own department.");
            }
        }

        Optional<DisasterReport> report = service.findReportForTask(taskId);
        if (report.isEmpty()) {
            throw ProtocolException.notFound("response task", taskId);
        }
        return Payloads.of("report", report.get());
    }
}
