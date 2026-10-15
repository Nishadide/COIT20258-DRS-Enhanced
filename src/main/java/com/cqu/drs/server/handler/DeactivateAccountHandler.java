package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AccountOperations;
import com.google.gson.JsonObject;

/**
 * Stops an account being used to sign in, without removing the record of what
 * that account did.
 *
 * <h2>Nobody may deactivate the account they are using</h2>
 * The account management screen in DRS-Initial refused this, and that refusal
 * is repeated here because a check made only in a client is a convenience: a
 * modified client, or a request built by hand, would not run it.
 *
 * <p>The rule also keeps the system administrable. Deactivating requires an
 * administrator's session, and an administrator cannot deactivate themselves,
 * so the administrator carrying out the deactivation is always left active.
 * The system therefore cannot be brought to a state where no administrator can
 * sign in, which no separate "last administrator" check is needed to
 * guarantee.
 */
public final class DeactivateAccountHandler implements OperationHandler {

    private final AccountOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public DeactivateAccountHandler(AccountOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ACCOUNT_DEACTIVATE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String username = Payloads.requireString(request.getPayload(), "username");

        if (username.equals(session.getUsername())) {
            throw ProtocolException.accessDenied(
                    "You cannot deactivate the account you are signed in as.");
        }

        try {
            service.deactivate(username, session.getUsername());
            return Payloads.acknowledged();
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("account", username);
        }
    }
}
