package com.cqu.drs.repository;

import com.cqu.drs.model.AuditLogEntry;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the audit log. Entries are only ever appended, never changed or
 * removed, so that the record of what was done during a response stays
 * trustworthy.
 */
public class AuditRepository {

    private static final String FILE_NAME = "audit.json";
    private static final Type LIST_TYPE = new TypeToken<ArrayList<AuditLogEntry>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates a repository backed by the store given.
     *
     * @param store the data store to read and write through
     */
    public AuditRepository(JsonDataStore store) {
        this.store = store;
    }

    /**
     * Every entry held, oldest first.
     *
     * @return the entries, or an empty list when none are held
     */
    public List<AuditLogEntry> findAll() {
        return store.load(FILE_NAME, LIST_TYPE);
    }

    /**
     * Adds an entry to the end of the log.
     *
     * @param entry the entry to append
     */
    public void append(AuditLogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Cannot append a null audit entry.");
        }
        List<AuditLogEntry> all = findAll();
        all.add(entry);
        store.store(FILE_NAME, all);
    }
}
