package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.server.SessionRegistry;
import com.cqu.drs.service.AuthenticationService;

/**
 * Ends a session.
 */
public final class LogoutHandler implements OperationHandler {

    private final AuthenticationService service;
    private final SessionRegistry sessions;

    /**
     * Creates the handler.
     *
     * @param service records the sign-out
     * @param sessions the sessions it needs
     */
    public LogoutHandler(AuthenticationService service, SessionRegistry sessions) {
        if (service == null || sessions == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
        this.sessions = sessions;
    }

    @Override
    public Operation operation() {
        return Operation.AUTH_LOGOUT;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        // Closing the session is what actually signs the person out: the
        // token stops being recognised immediately, so a copy of it taken
        // from the wire is worthless from here on.
        sessions.close(session.getToken());
        service.recordLogout(session.getUsername());
        return Payloads.acknowledged();
    }
}
