package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AccountOperations;
import com.google.gson.JsonObject;

/**
 * Returns a deactivated account to use, keeping the role and the department it
 * had before.
 *
 * Whether anything changed is reported rather than hidden, so pressing the
 * control twice is harmless and the second press says the account was already
 * active instead of implying work was done.
 */
public final class ActivateAccountHandler implements OperationHandler {

    private final AccountOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public ActivateAccountHandler(AccountOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ACCOUNT_ACTIVATE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String username = Payloads.requireString(request.getPayload(), "username");

        try {
            return Payloads.of("changed",
                    service.activate(username, session.getUsername()));
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("account", username);
        }
    }
}
