package com.cqu.drs.client;

import com.cqu.drs.migration.MigrationOperations;
import com.cqu.drs.migration.MigrationOutcome;
import com.cqu.drs.protocol.Operation;
import com.google.gson.JsonObject;

/**
 * Asks the server to carry the Assessment 2 data into the database.
 *
 * The client does no part of the migration. It holds no database connection
 * and, in the arrangement being demonstrated, it may not even be on the
 * machine the JSON files are on: the folder read is the server's, named by the
 * server's own configuration. All that travels from here is the request, and
 * all that comes back is the count of what was moved.
 */
public final class RemoteMigrationService implements MigrationOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteMigrationService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    /**
     * {@inheritDoc}
     *
     * The administrator's name is not sent. The server records the migration
     * against the user on the session, as it does for every other operation,
     * so a migration cannot be attributed to somebody who did not ask for it.
     */
    @Override
    public MigrationOutcome migrate(String requestedBy) {
        JsonObject result = client.sendOrThrow(Operation.ADMIN_MIGRATE, new JsonObject());
        return Results.one(result, "outcome", MigrationOutcome.class);
    }
}
