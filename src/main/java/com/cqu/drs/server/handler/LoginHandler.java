package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;
import com.cqu.drs.model.User;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.server.SessionRegistry;
import com.cqu.drs.service.AuthenticationService;

import java.util.Optional;

/**
 * Signs a user in and opens a session for them.
 *
 * Public, because nobody has a session before they sign in.
 */
public final class LoginHandler implements OperationHandler {

    private final AuthenticationService service;
    private final SessionRegistry sessions;

    /**
     * Creates the handler.
     *
     * @param service checks the credentials
     * @param sessions the sessions it needs
     */
    public LoginHandler(AuthenticationService service, SessionRegistry sessions) {
        if (service == null || sessions == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
        this.sessions = sessions;
    }

    @Override
    public Operation operation() {
        return Operation.AUTH_LOGIN;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String username = Payloads.requireString(request.getPayload(), "username");
        String password = Payloads.requireString(request.getPayload(), "password");

        // authenticate, not login. login would store the user in a field on
        // the service, which on a server is shared by every client, so the
        // last person to sign in would become everyone's current user.
        Optional<User> user = service.authenticate(username, password);

        if (user.isEmpty()) {
            // One message for all three reasons: unknown account, wrong
            // password, deactivated account. Distinguishing them would tell
            // someone guessing which usernames exist.
            throw new ProtocolException(ErrorCode.ACCESS_DENIED,
                    "The username or password was not recognised.");
        }

        Session opened = sessions.open(user.get());

        JsonObject result = new JsonObject();
        result.addProperty("sessionToken", opened.getToken());
        // Sent once, on this response only, and never again in either
        // direction. A client that loses it has to sign in again.
        result.addProperty("signingKey", opened.getSigningKey());
        result.add("user", com.cqu.drs.protocol.ProtocolCodec.gson()
                .toJsonTree(user.get(), User.class));
        return result;
    }
}
