package com.cqu.drs.client;

import com.cqu.drs.model.User;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Response;
import com.cqu.drs.service.AuthenticationOperations;
import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Signs in by asking the server, and holds the session it was given.
 *
 * <h2>What the client decides and what it does not</h2>
 * It decides nothing. The password is sent and the server answers. A client
 * that checked the password itself would be trusting the machine the password
 * was typed on, which is the one machine an attacker is assumed to control,
 * and it would need the stored hash to do it. The hash is never sent, so this
 * class has nothing it could check with even if it tried.
 *
 * <p>What it does keep is the token and the signing key the server issued, and
 * the user record for the screens to show a name on. The key is held in memory
 * on {@link DrsClient} and written nowhere.
 *
 * <h2>A current user on the client is correct, on the server it would not be</h2>
 * One person sits at one keyboard, so a single current user is exactly right
 * here. The server cannot have one, because it has as many signed-in users as
 * it has connected clients; it keeps each against the session token instead.
 * That is why {@code AuthenticationService} grew an {@code authenticate}
 * method that remembers nobody, which is the method the server calls.
 */
public final class RemoteAuthenticationService implements AuthenticationOperations {

    private final DrsClient client;

    private User currentUser;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteAuthenticationService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    /**
     * {@inheritDoc}
     *
     * A refusal is returned as an empty Optional rather than thrown, because
     * mistyping a password is an ordinary thing to do and the sign-in screen
     * already knows how to say so. The server sends one refusal for an unknown
     * account, a wrong password and a deactivated account alike, so nothing
     * here can distinguish them either.
     */
    @Override
    public Optional<User> login(String username, String password) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);

        Response response = client.send(Operation.AUTH_LOGIN, payload);

        if (!response.isOk()) {
            if (response.errorCodeOrNull() == ErrorCode.ACCESS_DENIED) {
                return Optional.empty();
            }
            throw new ProtocolException(
                    response.errorCodeOrNull() == null
                            ? ErrorCode.INTERNAL_ERROR : response.errorCodeOrNull(),
                    response.getMessage() == null
                            ? "The sign-in could not be completed." : response.getMessage());
        }

        JsonObject result = response.getPayload();
        // Recorded before the user is read, so that a reply which is somehow
        // missing the user still leaves the client able to send the signed
        // requests that would be needed to sign out again.
        client.setSession(Results.text(result, "sessionToken"),
                Results.text(result, "signingKey"));

        this.currentUser = Results.one(result, "user", User.class);
        return Optional.of(currentUser);
    }

    /**
     * {@inheritDoc}
     *
     * The server is told, so that it can close the session and record the
     * sign-out, and the session is then forgotten here whatever the server
     * said. A sign-out that failed because the server had already closed the
     * session, or because the network dropped, must still leave the person
     * signed out of the application in front of them. Leaving them apparently
     * signed in would be the worse outcome of the two.
     */
    @Override
    public void logout() {
        if (currentUser == null) {
            return;
        }
        try {
            client.sendOrThrow(Operation.AUTH_LOGOUT, new JsonObject());
        } catch (RuntimeException e) {
            // Any failure at all, because the person must end up signed out of
            // the application in front of them either way. Leaving them
            // apparently signed in would be the worse of the two outcomes.
            System.err.println("The sign-out was not acknowledged by the server: "
                    + e.getMessage());
        } finally {
            client.clearSession();
            this.currentUser = null;
        }
    }

    @Override
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /**
     * {@inheritDoc}
     *
     * The account changed is the one belonging to the session, decided by the
     * server from the token. No username is sent, so this operation cannot be
     * used to change somebody else's password.
     */
    @Override
    public void changeCurrentPassword(String newPassword) {
        if (currentUser == null) {
            throw new IllegalStateException("Nobody is signed in.");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("newPassword", newPassword);

        // A password the rules refuse, such as one too short, reaches the
        // screen as the same kind of failure the local service raises,
        // because the client translates every refusal in one place.
        client.sendOrThrow(Operation.AUTH_CHANGE_PASSWORD, payload);
    }
}
