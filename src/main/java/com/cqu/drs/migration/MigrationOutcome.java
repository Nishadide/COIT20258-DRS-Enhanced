package com.cqu.drs.migration;

/**
 * What a migration did, or why it did nothing.
 *
 * Returned to whoever asked for the migration rather than printed on the
 * server, because the person who asked is on another machine and a count they
 * cannot see is a count they have to take on trust.
 *
 * @param performed    whether anything was moved
 * @param detail       what happened, in words
 * @param departments  how many departments were carried over
 * @param accounts     how many staff accounts were carried over
 * @param reports      how many disaster reports were carried over, each with
 *                     its reporter, assessment, tasks, updates and alerts
 * @param alerts       how many alerts were carried over
 * @param auditEntries how many audit entries were carried over
 */
public record MigrationOutcome(boolean performed,
                               String detail,
                               int departments,
                               int accounts,
                               int reports,
                               int alerts,
                               int auditEntries) {

    /**
     * A migration that was asked for and was not needed.
     *
     * This is a success, not a failure. Asking twice is expected, and the
     * second answer should say so plainly rather than refuse.
     *
     * @param detail why nothing was done
     * @return the outcome
     */
    public static MigrationOutcome skipped(String detail) {
        return new MigrationOutcome(false, detail, 0, 0, 0, 0, 0);
    }

    /**
     * A migration that moved data.
     *
     * @param departments  departments carried over
     * @param accounts     accounts carried over
     * @param reports      reports carried over
     * @param alerts       alerts carried over
     * @param auditEntries audit entries carried over
     * @return the outcome
     */
    public static MigrationOutcome performed(int departments, int accounts, int reports,
                                             int alerts, int auditEntries) {
        return new MigrationOutcome(true,
                "The Assessment 2 data was carried into the database.",
                departments, accounts, reports, alerts, auditEntries);
    }

    /**
     * How many records were moved in total.
     *
     * @return the sum of every count
     */
    public int total() {
        return departments + accounts + reports + alerts + auditEntries;
    }

    /**
     * A description for a console or a log.
     *
     * @return one or two lines saying what happened
     */
    public String describe() {
        if (!performed) {
            return "Nothing was migrated. " + detail;
        }
        return detail + System.lineSeparator()
                + "  departments:  " + departments + System.lineSeparator()
                + "  accounts:     " + accounts + System.lineSeparator()
                + "  reports:      " + reports + System.lineSeparator()
                + "  alerts:       " + alerts + System.lineSeparator()
                + "  audit entries:" + auditEntries + System.lineSeparator()
                + "  total:        " + total();
    }
}
