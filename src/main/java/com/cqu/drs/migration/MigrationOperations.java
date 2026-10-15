package com.cqu.drs.migration;

/**
 * Carrying the Assessment 2 data into the Assessment 3 database.
 *
 * Expressed as an interface for the same reason the other operations are: it
 * is implemented once on the server, by {@link DataMigrationService}, and once
 * on the client, by a class that asks the server to do it. The console tool
 * that triggers the migration is written against this and does not know which
 * of the two it holds.
 */
public interface MigrationOperations {

    /**
     * Carries the data over, or reports that it has already been carried over.
     *
     * @param requestedBy the administrator asking for it, recorded in the
     *                    audit trail against the migration
     * @return what was moved, or why nothing was
     */
    MigrationOutcome migrate(String requestedBy);
}
