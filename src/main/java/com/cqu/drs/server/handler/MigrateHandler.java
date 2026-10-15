package com.cqu.drs.server.handler;

import com.cqu.drs.migration.MigrationOperations;
import com.cqu.drs.migration.MigrationOutcome;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;

/**
 * Carries the Assessment 2 data into the database, on request.
 *
 * <h2>Why the migration is an operation rather than a script</h2>
 * The specification asks for the data to be loaded into the database through
 * the server, and this is what that means in practice. The client holds no
 * database connection, so it cannot write the rows itself; a script run
 * against MySQL would move the data but would bypass the server, the business
 * rules and the audit trail, which is the arrangement three tiers exists to
 * prevent.
 *
 * <p>Asking the server instead means the migrated rows are written by the same
 * repositories, in the same transactions, under the same constraints as every
 * row the running application writes, and that the migration appears in the
 * audit trail as something an identified administrator did at a known time.
 *
 * <h2>Administrators only, and the refusals say which problem it is</h2>
 * The role check is made by the dispatcher from the role on the session. The
 * refusals this handler itself produces distinguish the two ways a migration
 * can be declined: a database that already holds reports is a conflict, which
 * the person can resolve by starting from an empty one, and anything else is
 * reported as what it is rather than as the person's fault.
 */
public final class MigrateHandler implements OperationHandler {

    private final MigrationOperations migration;

    /**
     * Creates the handler.
     *
     * @param migration the migration this handler runs
     */
    public MigrateHandler(MigrationOperations migration) {
        if (migration == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.migration = migration;
    }

    @Override
    public Operation operation() {
        return Operation.ADMIN_MIGRATE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        try {
            MigrationOutcome outcome = migration.migrate(session.getUsername());
            return Payloads.of("outcome", outcome);

        } catch (IllegalStateException e) {
            // The database is not in a state where this can be done, which is
            // not a fault in the request. CONFLICT says that, where
            // VALIDATION_FAILED would say the request was wrong and
            // INTERNAL_ERROR would say the server had broken.
            throw new ProtocolException(ErrorCode.CONFLICT, e.getMessage(), e);

        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
