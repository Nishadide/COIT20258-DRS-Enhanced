package com.cqu.drs.repository;

import com.cqu.drs.model.AuditLogEntry;

import java.util.List;

/**
 * Stores the audit log. Entries are appended and never amended, which is what
 * allows the log to stand as evidence of who did what and when.
 */
public interface AuditRepository {

    /**
     * Every entry held, oldest first.
     *
     * @return the entries
     */
    List<AuditLogEntry> findAll();

    /**
     * Appends one entry to the log.
     *
     * @param entry the entry to append
     */
    void append(AuditLogEntry entry);
}
