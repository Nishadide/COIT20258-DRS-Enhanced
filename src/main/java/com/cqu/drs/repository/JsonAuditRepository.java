package com.cqu.drs.repository;

import com.cqu.drs.model.AuditLogEntry;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-file implementation. Stores the audit log. Entries are only ever appended, never changed or
 * removed, so that the record of what was done during a response stays
 * trustworthy.
 */
public class JsonAuditRepository implements AuditRepository {

    private static final String FILE_NAME = "audit.json";
    private static final Type LIST_TYPE = new TypeToken<ArrayList<AuditLogEntry>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates a repository backed by the store given.
     *
     * @param store the data store to read and write through
     */
    public JsonAuditRepository(JsonDataStore store) {
        this.store = store;
    }

    /**
     * Every entry held, oldest first.
     *
     * @return the entries, or an empty list when none are held
     */
    @Override
    public List<AuditLogEntry> findAll() {
        return store.load(FILE_NAME, LIST_TYPE);
    }

    /**
     * Adds an entry to the end of the log.
     *
     * @param entry the entry to append
     */
    /**
     * {@inheritDoc}
     *
     * This store keeps no hash chain, so it reports that it cannot tell
     * rather than reporting that all is well. A clean result from a store with
     * no means of detecting alteration would be a claim it cannot support, and
     * an audit trail that falsely claims to be verified is worse than one that
     * admits it is not.
     *
     * <p>This is also one of the reasons the JSON stores are not used by
     * DRS-Enhanced: the non-repudiation the specification asks for is a
     * property the MySQL store has and this one does not.
     */
    @Override
    public AuditChainStatus verifyChain() {
        return AuditChainStatus.notSupported("The JSON audit store");
    }

    @Override
    public void append(AuditLogEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Cannot append a null audit entry.");
        }
        List<AuditLogEntry> all = findAll();
        all.add(entry);
        store.store(FILE_NAME, all);
    }
}
