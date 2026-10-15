package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.AuthenticationService;

/**
 * Changes the password of the account the session belongs to.
 */
public final class ChangePasswordHandler implements OperationHandler {

    private final AuthenticationService service;

    /**
     * Creates the handler.
     *
     * @param service changes the password
     */
    public ChangePasswordHandler(AuthenticationService service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.AUTH_CHANGE_PASSWORD;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String newPassword = Payloads.requireString(request.getPayload(), "newPassword");

        // The account changed is the one the session belongs to, never one
        // the request names. Otherwise anyone signed in could change anyone
        // else's password by asking.
        try {
            service.changePassword(session.getUsername(), newPassword);
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
        return Payloads.acknowledged();
    }
}
