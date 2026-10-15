package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AuditOperations;
import com.google.gson.JsonObject;

/**
 * The audit trail, oldest entry first.
 *
 * Reading is offered and writing is not, and there is no operation anywhere in
 * the protocol that lets a client add an entry. Entries are written by the
 * server as a consequence of work it has just carried out and can vouch for.
 * A trail that accepted what it was told would be evidence of nothing, since
 * anyone able to reach the server could write an entry blaming someone else.
 */
public final class FindAllAuditHandler implements OperationHandler {

    private final AuditOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public FindAllAuditHandler(AuditOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.AUDIT_FIND_ALL;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("entries", service.findAll());
    }
}
