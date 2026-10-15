package com.cqu.drs.client;

import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.AccountOperations;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * Manages staff accounts by asking the server.
 *
 * <h2>The accounts that come back carry no password hash</h2>
 * They cannot: the protocol's own adapter drops it before a user is written to
 * the connection. So the administrator's screen shows names, roles and whether
 * each account is active, and holds nothing that could be used to sign in as
 * anybody.
 *
 * <h2>Who did it is not sent</h2>
 * Every method takes the acting administrator's username, because the
 * interface it implements is also implemented by the local service, which has
 * no session to read it from. This implementation ignores that argument and
 * sends nothing in its place. The server records the change against the user
 * on the session, so a modified client cannot attribute its own actions to
 * someone else, which is exactly what it could do if the name travelled in the
 * payload.
 */
public final class RemoteAccountService implements AccountOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteAccountService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public User create(String username, String plainPassword, String fullName,
                       UserRole role, String departmentId, String createdBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", plainPassword);
        payload.addProperty("fullName", fullName);
        payload.addProperty("role", role == null ? null : role.name());
        if (departmentId != null && !departmentId.isBlank()) {
            payload.addProperty("departmentId", departmentId);
        }

        JsonObject result = client.sendOrThrow(Operation.ACCOUNT_CREATE, payload);
        return Results.one(result, "user", User.class);
    }

    @Override
    public void deactivate(String username, String deactivatedBy) {
        client.sendOrThrow(Operation.ACCOUNT_DEACTIVATE, named(username));
    }

    @Override
    public boolean activate(String username, String activatedBy) {
        JsonObject result =
                client.sendOrThrow(Operation.ACCOUNT_ACTIVATE, named(username));
        return Results.flag(result, "changed");
    }

    @Override
    public void assignRole(String username, UserRole role, String changedBy) {
        JsonObject payload = named(username);
        payload.addProperty("role", role == null ? null : role.name());

        client.sendOrThrow(Operation.ACCOUNT_ASSIGN_ROLE, payload);
    }

    @Override
    public List<User> findAll() {
        JsonObject result = client.sendOrThrow(Operation.ACCOUNT_FIND_ALL, new JsonObject());
        return Results.list(result, "users", User.class);
    }

    /**
     * {@inheritDoc}
     *
     * Answered from the list the server returns rather than by a request of
     * its own, because the protocol offers no operation for fetching one
     * account and adding one would widen what an administrator's client can
     * ask for without widening what it can do.
     */
    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(user -> username.equals(user.getUsername()))
                .findFirst();
    }

    private static JsonObject named(String username) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        return payload;
    }

}
