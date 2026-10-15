package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.AccountOperations;
import com.google.gson.JsonObject;

/**
 * Every staff account, for the administrator's account management screen.
 *
 * The accounts are written by the protocol's Gson, which handles the user
 * hierarchy and leaves the password hash behind. That matters most here: this
 * is the one operation that would otherwise hand a client the hash of every
 * account in the system in a single reply.
 */
public final class FindAllAccountsHandler implements OperationHandler {

    private final AccountOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public FindAllAccountsHandler(AccountOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.ACCOUNT_FIND_ALL;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        return Payloads.of("users", service.findAll());
    }
}
