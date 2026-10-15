package com.cqu.drs.server.handler;

import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AccountOperations;
import com.google.gson.JsonObject;

/**
 * Changes the role an account holds.
 *
 * <h2>Nobody may change their own role</h2>
 * An administrator who granted themselves a different role would be removing
 * their own access to the screen they were standing on, and if they were the
 * only administrator the system would be left with nobody able to manage
 * accounts at all. The role on the open session is also copied at sign-in and
 * is not re-read per request, so the change would not take effect until the
 * next sign-in, which is the kind of mismatch that makes an access control
 * fault hard to see.
 *
 * <p>Refusing the self-change removes all three problems and costs nothing: an
 * administrator who should hold a different role is given it by another
 * administrator.
 */
public final class AssignRoleHandler implements OperationHandler {

    private final AccountOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public AssignRoleHandler(AccountOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ACCOUNT_ASSIGN_ROLE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String username = Payloads.requireString(request.getPayload(), "username");
        UserRole role = Payloads.requireEnum(request.getPayload(), "role", UserRole.class);

        if (username.equals(session.getUsername())) {
            throw ProtocolException.accessDenied(
                    "You cannot change the role of the account you are signed in as. "
                            + "Ask another administrator to make the change.");
        }

        try {
            service.assignRole(username, role, session.getUsername());
            return Payloads.acknowledged();
        } catch (IllegalArgumentException e) {
            throw ProtocolException.notFound("account", username);
        }
    }
}
