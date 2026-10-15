package com.cqu.drs.repository;

import com.cqu.drs.model.AuditLogEntry;

import java.util.List;

/**
 * Stores the audit trail: who did what, and when.
 *
 * Entries are appended and never altered. There is deliberately no update and
 * no delete on this interface, because a record of what happened that can be
 * changed afterwards is not a record of what happened.
 */
public interface AuditRepository {

    /**
     * Every entry held, oldest first.
     *
     * @return the entries
     */
    List<AuditLogEntry> findAll();

    /**
     * Adds an entry to the end of the trail.
     *
     * @param entry the entry to append
     */
    void append(AuditLogEntry entry);

    /**
     * Checks that nothing in the trail has been altered since it was written.
     *
     * The MySQL implementation hash-chains its entries, so each one depends on
     * every entry before it and altering or removing any of them is detectable
     * here. This is what turns the non-repudiation requirement into something
     * that can be demonstrated rather than asserted.
     *
     * <p>A store that keeps no chain reports that it cannot tell, rather than
     * reporting that all is well. The distinction matters: "I checked and
     * found nothing wrong" and "I have no way to check" are different answers,
     * and only one of them is evidence.
     *
     * @return what was found
     */
    AuditChainStatus verifyChain();
}
