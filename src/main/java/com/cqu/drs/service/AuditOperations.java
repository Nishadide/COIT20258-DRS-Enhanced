package com.cqu.drs.service;

import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.repository.AuditChainStatus;

import java.util.List;

/**
 * Reading the audit trail, and checking it has not been altered.
 *
 * <h2>There is deliberately no way to write an entry</h2>
 * Entries are written by the server, as a consequence of operations it has
 * just carried out and can vouch for. Offering a client a way to add an entry
 * would mean anyone able to reach the server could write whatever they liked
 * into the record of what happened, including entries blaming someone else.
 *
 * <p>An audit trail that accepts what it is told is not evidence of anything,
 * so this interface reads and verifies and does nothing else.
 */
public interface AuditOperations {

    /**
     * Every entry, oldest first.
     *
     * @return the entries
     */
    List<AuditLogEntry> findAll();

    /**
     * Checks the trail has not been altered since it was written.
     *
     * @return what was found
     */
    AuditChainStatus verifyChain();
}
