package com.cqu.drs.service;

import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.repository.AuditRepository;

import java.util.List;
import java.util.UUID;

/**
 * Records what was done in the system, so that the actions taken during a
 * response can be reviewed afterwards.
 */
public class AuditService {

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
    public List<AuditLogEntry> findAll() {
        return auditRepository.findAll();
    }
}
