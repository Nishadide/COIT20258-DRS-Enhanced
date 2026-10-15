package com.cqu.drs.repository;

/**
 * Thrown when a report is stored under a reference another report already
 * holds.
 *
 * This exists because of a race that Assessment 2 could not have and
 * Assessment 3 can. {@code ReportService.generateReference} asks how many
 * reports are stored, adds one, and checks the result is free. In a desktop
 * prototype with one user at the keyboard that is sound. With a server
 * handling several clients at once, two threads can read the same count, both
 * find DR-0005 unused, and both try to store it.
 *
 * <p>The primary key stops the second one, so no data is lost. What matters is
 * what happens next. If the repository quietly turned the second write into an
 * update, the later report would overwrite the earlier one and a disaster
 * would vanish with nothing logged. Failing loudly instead lets the caller
 * take a new reference and try again, which is the correct outcome: both
 * reports are stored, under different references.
 *
 * <p>It is a distinct type rather than a message inside a general storage
 * failure so that the retry can catch this and only this. Retrying any storage
 * failure would mean retrying a genuine database fault in a loop.
 */
public class DuplicateReferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reference;

    /**
     * Creates the exception.
     *
     * @param reference the reference already in use
     * @param cause     the duplicate key failure from the database
     */
    public DuplicateReferenceException(String reference, Throwable cause) {
        super("A disaster report is already stored under the reference "
                + reference + ".", cause);
        this.reference = reference;
    }

    /**
     * The reference that was already taken.
     *
     * @return the reference
     */
    public String getReference() {
        return reference;
    }
}
