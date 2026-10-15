package com.cqu.drs.migration;

import java.util.Optional;

/**
 * Remembers which one-off data movements have already been carried out.
 *
 * <h2>Why the record is kept in the database being migrated into</h2>
 * The migration has to be safe to ask for twice, because it will be: the
 * person running the demonstration does not know whether somebody else
 * already ran it, and a migration that ran twice would duplicate or overwrite
 * records. So the question "has this already happened" has to be answerable,
 * and the only place that can answer it reliably is the database itself.
 *
 * <p>A flag held in the server's memory would be forgotten on restart. A file
 * beside the application would be left behind when the database was dropped
 * and recreated, and would then claim a migration had happened into a database
 * that no longer held any of it. Keeping the record in the same database means
 * the record and the data it describes are created, copied and destroyed
 * together.
 */
public interface MigrationLedger {

    /**
     * Whether a movement has already been carried out.
     *
     * @param item the name the movement is recorded under
     * @return true when it has already happened
     */
    boolean hasRun(String item);

    /**
     * What was recorded when it happened.
     *
     * @param item the name the movement is recorded under
     * @return the detail recorded, or an empty Optional when it has not run
     */
    Optional<String> detailOf(String item);

    /**
     * Records that a movement has been carried out.
     *
     * @param item   the name to record it under
     * @param detail what was moved, in words, for whoever reads this later
     */
    void record(String item, String detail);
}
