package com.cqu.drs.client;

import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.repository.AuditChainStatus;
import com.cqu.drs.service.AuditOperations;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Reads the audit trail from the server, and asks it whether the trail still
 * adds up.
 *
 * <h2>There is nothing here that writes</h2>
 * That is the whole design rather than an omission. Entries are written by the
 * server as a consequence of work it carried out and can vouch for. If a
 * client could add one, anyone who could reach the server could write entries
 * saying whatever they liked, including entries blaming another user, and the
 * trail would then be evidence of nothing.
 *
 * <p>So this class has two methods, both of which only ask questions, and the
 * protocol declares no operation that would let it do more. The screen that
 * used to write its own entry after changing a department no longer does: the
 * server writes that entry as part of carrying out the change.
 *
 * <h2>Verification happens on the server</h2>
 * The hashes are recomputed over the stored rows, where the rows are. Only the
 * verdict travels. A client cannot be allowed to decide this for itself, both
 * because it does not hold the rows and because a verdict computed by a client
 * is a verdict whoever controls that client can choose.
 */
public final class RemoteAuditService implements AuditOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteAuditService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public List<AuditLogEntry> findAll() {
        JsonObject result = client.sendOrThrow(Operation.AUDIT_FIND_ALL, new JsonObject());
        return Results.list(result, "entries", AuditLogEntry.class);
    }

    @Override
    public AuditChainStatus verifyChain() {
        JsonObject result =
                client.sendOrThrow(Operation.AUDIT_VERIFY_CHAIN, new JsonObject());
        return Results.one(result, "status", AuditChainStatus.class);
    }
}
