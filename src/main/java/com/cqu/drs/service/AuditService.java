package com.cqu.drs.service;

import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.repository.AuditChainStatus;
import com.cqu.drs.repository.AuditRepository;

import java.util.List;
import java.util.UUID;

/**
 * Records what was done in the system, so that the actions taken during a
 * response can be reviewed afterwards.
 */
public class AuditService implements AuditOperations {

    private final AuditRepository auditRepository;

    /**
     * Creates the service.
     *
     * @param auditRepository where entries are stored
     */
    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Appends an entry to the audit log.
     *
     * @param actor  who performed the action, or null for an anonymous action
     * @param action a description of what was done
     */
    public void record(String actor, String action) {
        auditRepository.append(
                new AuditLogEntry(UUID.randomUUID().toString(), actor, action));
    }

    /**
     * The whole audit log, oldest first.
     *
     * @return the entries
     */
    /**
     * {@inheritDoc}
     *
     * Delegated to the store, because whether the trail is tamper evident is a
     * property of how it is kept. The MySQL store hash chains its entries and
     * can answer; the JSON store cannot and says so.
     */
    @Override
    public AuditChainStatus verifyChain() {
        return auditRepository.verifyChain();
    }

    @Override
    public List<AuditLogEntry> findAll() {
        return auditRepository.findAll();
    }
}
