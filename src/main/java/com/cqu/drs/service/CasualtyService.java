package com.cqu.drs.service;

import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.repository.CasualtyRepository;
import com.cqu.drs.repository.DisasterReportRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The casualty registry.
 *
 * <h2>What goes into the audit trail, and what deliberately does not</h2>
 * Every registration, retriage and referral is recorded, because these are
 * decisions about people that somebody is accountable for, and because the
 * specification's non-repudiation requirement is only meaningful if the
 * sensitive operations are the ones covered.
 *
 * <p>What the entries do not contain is any casualty's name, contact number or
 * notes. The audit trail is readable by administrators, who are not permitted
 * to read casualty records; writing a name into an entry would hand it to them
 * through the back door and would put personal data into a table that is
 * deliberately append only and never deleted. The entries name the record and
 * the category, which is enough to reconstruct who did what to which record
 * without disclosing who the record is about.
 *
 * <h2>The disaster is checked before anything is written</h2>
 * A casualty recorded against a reference that does not exist is a record
 * nobody will ever find again, and the foreign key would refuse it anyway with
 * a message naming a constraint rather than the reference.
 */
public class CasualtyService implements CasualtyOperations {

    private final CasualtyRepository casualtyRepository;
    private final DisasterReportRepository reportRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param casualtyRepository where casualty records are stored
     * @param reportRepository   used to check the disaster exists
     * @param auditService       where the decisions are recorded
     */
    public CasualtyService(CasualtyRepository casualtyRepository,
                           DisasterReportRepository reportRepository,
                           AuditService auditService) {
        if (casualtyRepository == null || reportRepository == null
                || auditService == null) {
            throw new IllegalArgumentException("A casualty service needs its "
                    + "repositories and an audit service.");
        }
        this.casualtyRepository = casualtyRepository;
        this.reportRepository = reportRepository;
        this.auditService = auditService;
    }

    @Override
    public Casualty register(String reference, String name, Integer age,
                             String contactNumber, TriageCategory triage,
                             String location, String notes, String registeredBy) {
        requireReport(reference);

        Casualty casualty = new Casualty(
                "CA-" + UUID.randomUUID().toString()
                        .substring(0, DrsConstants.SHORT_ID_LENGTH),
                reference, name, age, contactNumber, triage, location, notes,
                registeredBy);

        casualtyRepository.save(casualty);

        // No name in the entry. See the note on this class.
        auditService.record(registeredBy,
                "Registered casualty " + casualty.describeWithoutIdentity());
        return casualty;
    }

    @Override
    public Casualty retriage(String casualtyId, TriageCategory triage, String changedBy) {
        Casualty casualty = require(casualtyId);
        TriageCategory previous = casualty.getTriage();

        casualty.retriage(triage, changedBy);
        casualtyRepository.save(casualty);

        auditService.record(changedBy, "Retriaged casualty " + casualtyId
                + " on " + casualty.getReferenceNumber()
                + " from " + (previous == null ? "none" : previous.getLabel())
                + " to " + triage.getLabel());
        return casualty;
    }

    @Override
    public Casualty refer(String casualtyId, String hospital, String referredBy) {
        Casualty casualty = require(casualtyId);

        // Throws when the casualty is recorded as deceased, which is a rule
        // about the record rather than about the request, so it is left to the
        // model where it belongs.
        casualty.referTo(hospital, referredBy);
        casualtyRepository.save(casualty);

        auditService.record(referredBy, "Referred casualty " + casualtyId
                + " on " + casualty.getReferenceNumber() + " to " + hospital);
        return casualty;
    }

    @Override
    public Casualty updateStatus(String casualtyId, CasualtyStatus status,
                                 String changedBy) {
        Casualty casualty = require(casualtyId);

        casualty.updateStatus(status, changedBy);
        casualtyRepository.save(casualty);

        auditService.record(changedBy, "Set casualty " + casualtyId
                + " on " + casualty.getReferenceNumber() + " to " + status.getLabel());
        return casualty;
    }

    @Override
    public List<Casualty> findForReport(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Name the disaster.");
        }
        return casualtyRepository.findForReport(reference);
    }

    @Override
    public Map<TriageCategory, Integer> summaryFor(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Name the disaster.");
        }
        return casualtyRepository.countByTriage(reference);
    }

    private void requireReport(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Name the disaster.");
        }
        if (reportRepository.findByReference(reference).isEmpty()) {
            throw new IllegalArgumentException(
                    "No report holds the reference " + reference + ".");
        }
    }

    private Casualty require(String casualtyId) {
        if (casualtyId == null || casualtyId.isBlank()) {
            throw new IllegalArgumentException("Name the casualty record.");
        }
        return casualtyRepository.findById(casualtyId).orElseThrow(
                () -> new IllegalArgumentException(
                        "No casualty record holds the identifier " + casualtyId + "."));
    }
}
