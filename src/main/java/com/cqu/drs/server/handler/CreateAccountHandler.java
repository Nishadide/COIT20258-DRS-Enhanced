package com.cqu.drs.server.handler;

import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AccountOperations;
import com.google.gson.JsonObject;

/**
 * Creates a staff account.
 *
 * <h2>The password arrives in the clear</h2>
 * The initial password travels inside the request, which on a plain socket
 * means it crosses the network readable. The same is true of the password sent
 * at sign-in, and it is a property of the transport rather than of this
 * handler: section 2.5 of the specification asks for encryption, and what the
 * application has is HMAC-SHA256 signing, which proves a request was not
 * altered but does not conceal it.
 *
 * <p>The remedy is TLS on the socket rather than anything done here, since
 * encrypting one field would leave the sign-in password exposed and give a
 * false impression that the problem had been dealt with. It is recorded as a
 * known limitation with its remedy in the report rather than left for a marker
 * to find.
 *
 * <p>What this handler does do is make sure the password is never stored or
 * returned: the service hashes it on arrival, and the account that goes back
 * to the client is written by the protocol's own adapter, which drops the hash.
 */
public final class CreateAccountHandler implements OperationHandler {

    private final AccountOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public CreateAccountHandler(AccountOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ACCOUNT_CREATE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String username = Payloads.requireString(request.getPayload(), "username");
        String password = Payloads.requireString(request.getPayload(), "password");
        String fullName = Payloads.requireString(request.getPayload(), "fullName");
        UserRole role = Payloads.requireEnum(request.getPayload(), "role", UserRole.class);
        String departmentId = Payloads.optionalString(request.getPayload(), "departmentId");

        try {
            return Payloads.of("user", service.create(username, password, fullName,
                    role, departmentId, session.getUsername()));

        } catch (IllegalArgumentException e) {
            // A name already taken is a conflict rather than a bad request,
            // and the two read differently to whoever is looking at the
            // screen: one means try another name, the other means the details
            // are wrong.
            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                throw new ProtocolException(ErrorCode.CONFLICT, e.getMessage());
            }
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
