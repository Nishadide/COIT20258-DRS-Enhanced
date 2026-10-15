package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AuditOperations;
import com.google.gson.JsonObject;

/**
 * Checks that the audit trail has not been altered since it was written.
 *
 * <h2>Why a client is allowed to ask this</h2>
 * This is the non-repudiation requirement made visible. The trail is
 * hash-chained, so an entry cannot be edited or removed without the
 * recomputed hashes disagreeing with the stored ones, and an administrator can
 * ask at any time whether they still agree.
 *
 * <p>The answer is computed on the server, over the stored rows, and only the
 * verdict travels. A client cannot be given the means to decide this for
 * itself: it holds no rows, and a verdict a client computed would be a verdict
 * whoever controls that client could choose.
 *
 * <p>A result saying the chain is not supported is not a failure. The JSON
 * store inherited from DRS-Initial keeps no chain, and says so rather than
 * reporting a clean result it has no way to establish.
 */
public final class VerifyChainHandler implements OperationHandler {

    private final AuditOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public VerifyChainHandler(AuditOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.AUDIT_VERIFY_CHAIN;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("status", service.verifyChain());
    }
}
