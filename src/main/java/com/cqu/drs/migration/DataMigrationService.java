package com.cqu.drs.migration;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.User;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Carries the data DRS-Initial wrote into JSON files across into the database.
 *
 * <h2>It reads and writes through repositories, not SQL</h2>
 * The source is the JSON repositories from Assessment 2 and the destination is
 * the MySQL repositories from Assessment 3, both held here as the interfaces
 * they implement. So this class contains no SQL, no file handling and no
 * knowledge of either format.
 *
 * <p>That is worth more than tidiness. Writing through the repositories means
 * every constraint, relationship and transaction the application relies on
 * applies to the migrated rows as well: a report is written across its five
 * tables in one transaction, an audit entry is hash-chained as it goes in, and
 * anything the schema refuses is refused here too. A migration written as
 * INSERT statements would bypass all of it and could leave the database in a
 * state the application cannot produce and may not be able to read.
 *
 * <p>It is also what makes this testable without a database at all: the test
 * hands it JSON repositories on both sides and checks what arrives.
 *
 * <h2>The order is not arbitrary</h2>
 * Departments, then accounts, then reports, then alerts, then the audit trail.
 * A responder's account refers to a department and a dispatched task refers to
 * one, so departments exist first or the foreign keys refuse the rows. Alerts
 * attached to a report are written with the report; the pass over the alerts
 * afterwards catches any that are not attached to one.
 *
 * <h2>What the migration deliberately does not do</h2>
 * It does not create accounts through the account service, which would hash a
 * new password and leave everybody unable to sign in with the password they
 * had. It moves the stored hash as it stands. It also writes no audit entry
 * per record moved: the trail would be buried under hundreds of entries saying
 * a record was copied. One entry records the migration, and the entries
 * carried over from Assessment 2 keep their own actors and times.
 */
public final class DataMigrationService implements MigrationOperations {

    /**
     * The name the migration is recorded under in the ledger.
     *
     * Fixed, and not derived from anything, because the question asked on the
     * second run has to be the same question answered on the first.
     */
    public static final String LEDGER_ITEM = "data.migration.drs-initial";

    private final MigrationSource source;

    private final DepartmentRepository departments;
    private final UserRepository accounts;
    private final DisasterReportRepository reports;
    private final AlertRepository alerts;
    private final AuditRepository auditTrail;

    private final MigrationLedger ledger;

    /**
     * Creates the migration.
     *
     * @param source      where the Assessment 2 data is read from
     * @param departments the destination for departments
     * @param accounts    the destination for staff accounts
     * @param reports     the destination for disaster reports
     * @param alerts      the destination for alerts
     * @param auditTrail  the destination for the audit trail
     * @param ledger      where the fact that this has happened is recorded
     */
    public DataMigrationService(MigrationSource source,
                                DepartmentRepository departments,
                                UserRepository accounts,
                                DisasterReportRepository reports,
                                AlertRepository alerts,
                                AuditRepository auditTrail,
                                MigrationLedger ledger) {
        if (source == null || departments == null || accounts == null || reports == null
                || alerts == null || auditTrail == null || ledger == null) {
            throw new IllegalArgumentException(
                    "A migration needs a source, five destinations and a ledger.");
        }
        this.source = source;
        this.departments = departments;
        this.accounts = accounts;
        this.reports = reports;
        this.alerts = alerts;
        this.auditTrail = auditTrail;
        this.ledger = ledger;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when the destination already holds reports
     *         that this migration did not put there
     */
    @Override
    public MigrationOutcome migrate(String requestedBy) {
        Optional<String> already = ledger.detailOf(LEDGER_ITEM);
        if (already.isPresent()) {
            return MigrationOutcome.skipped(
                    "This migration has already been carried out. " + already.get());
        }

        requireDestinationHasNoReports();

        int movedDepartments = migrateDepartments();
        int movedAccounts = migrateAccounts();
        int movedReports = migrateReports();
        int movedAlerts = migrateAlerts();
        int movedAudit = migrateAuditTrail();

        MigrationOutcome outcome = MigrationOutcome.performed(movedDepartments,
                movedAccounts, movedReports, movedAlerts, movedAudit);

        // Recorded in the trail before the ledger, so that if the ledger write
        // fails the trail still shows what was done and the failure is not
        // silent. Writing it after the migrated entries also puts it in the
        // right place in the chain: everything before it came from Assessment
        // 2, everything after it happened here.
        auditTrail.append(new AuditLogEntry(UUID.randomUUID().toString(),
                requestedBy == null ? "system" : requestedBy,
                "Migrated the Assessment 2 data: " + outcome.total() + " records ("
                        + movedReports + " reports, " + movedAccounts + " accounts, "
                        + movedAudit + " earlier audit entries)"));

        ledger.record(LEDGER_ITEM, outcome.total() + " records carried over: "
                + movedDepartments + " departments, " + movedAccounts + " accounts, "
                + movedReports + " reports, " + movedAlerts + " alerts, "
                + movedAudit + " audit entries, requested by "
                + (requestedBy == null ? "system" : requestedBy) + ".");

        return outcome;
    }

    /**
     * Refuses to migrate into a database that is already being used.
     *
     * <h2>Why this check is here</h2>
     * Both systems allocate references from DR-0001 upwards, so the reports in
     * the JSON files and the reports lodged since the server started have the
     * same names. Migrating on top of them would not fail: saving a report is
     * an upsert, so each migrated report would quietly replace a live one, and
     * a disaster reported this morning would become a disaster reported last
     * month. Nothing would say so.
     *
     * <p>Refusing is the right answer rather than merging or renumbering. A
     * migration is a one-off move into an empty database, and an empty
     * database is something the person asking can arrange. Guessing which of
     * two reports called DR-0001 is wanted is not something this code can do.
     *
     * <p>Departments and accounts are not included in the check, because both
     * are reference data the server seeds at startup so that somebody can sign
     * in at all. Those are expected to be there and are meant to be replaced.
     */
    private void requireDestinationHasNoReports() {
        int existing = reports.count();
        if (existing > 0) {
            throw new IllegalStateException("The database already holds " + existing
                    + " disaster report(s), so the Assessment 2 data cannot be "
                    + "migrated into it: both systems number reports from DR-0001, "
                    + "and the migrated reports would replace the ones already "
                    + "there. Migrate into an empty database, which SchemaTool "
                    + "--drop-and-recreate will give you.");
        }
    }

    private int migrateDepartments() {
        int moved = 0;
        for (Department department : source.departments()) {
            if (department == null) {
                continue;
            }
            // Saved rather than skipped when one of the same identifier is
            // already there, because the availability recorded in Assessment 2
            // is part of the data being carried over.
            departments.save(department);
            moved++;
        }
        return moved;
    }

    /**
     * Carries the staff accounts over, keeping their passwords.
     *
     * <h2>The clash this has to handle</h2>
     * The server seeds three accounts at startup so that an empty database can
     * be signed into. Assessment 2 wrote accounts with the same three
     * usernames and different internal identifiers. Saving the migrated
     * account as it stands would insert a second row with the same username,
     * which the unique key refuses, and the migration would fail on the first
     * account it touched.
     *
     * <p>So where a username is already present, the migrated account is given
     * the identifier of the row already there before it is saved, which makes
     * the write an update: same row, Assessment 2's password, name, role and
     * active flag. The internal identifier is a surrogate that nothing else
     * refers to, so nothing is lost by keeping the destination's.
     *
     * @return how many accounts were carried over
     */
    private int migrateAccounts() {
        int moved = 0;
        for (User user : source.accounts()) {
            if (user == null) {
                continue;
            }
            accounts.findByUsername(user.getUsername())
                    .ifPresent(existing -> user.setUserId(existing.getUserId()));
            accounts.save(user);
            moved++;
        }
        return moved;
    }

    /**
     * Carries the reports over, each with everything hanging off it.
     *
     * Oldest first, so that anything reading the destination in insertion
     * order sees them in the order they were reported rather than the order
     * they happened to sit in a file.
     *
     * @return how many reports were carried over
     */
    private int migrateReports() {
        List<DisasterReport> ordered = new ArrayList<>(source.reports());
        ordered.sort(Comparator.comparing(DisasterReport::getReportedAt,
                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));

        int moved = 0;
        for (DisasterReport report : ordered) {
            if (report == null) {
                continue;
            }
            // save, not insert. insert refuses a reference that already
            // exists, which is what lodging a new report wants; a migration
            // is moving a report that already has its reference and must be
            // able to write it.
            reports.save(report);
            moved++;
        }
        return moved;
    }

    /**
     * Carries over any alert the reports did not already bring with them.
     *
     * An alert attached to a report is written when that report is written, so
     * most of these are already present and are saved again, which updates the
     * row rather than duplicating it. The pass exists for the alerts that are
     * not attached to a report, which would otherwise be left behind.
     *
     * @return how many alerts were written
     */
    private int migrateAlerts() {
        int moved = 0;
        for (Alert alert : source.alerts()) {
            if (alert == null) {
                continue;
            }
            alerts.save(alert);
            moved++;
        }
        return moved;
    }

    /**
     * Carries the audit trail over, oldest entry first.
     *
     * <h2>The entries move; the chain does not</h2>
     * The JSON store kept no hashes, so there is no chain to bring. Each entry
     * is appended to the destination, which assigns it the next sequence
     * number and hashes it onto the entry before it. The result is a chain
     * that is sound from its first entry onwards, covering the Assessment 2
     * entries in their original order with their original actors and times.
     *
     * <p>What that chain cannot do is prove the Assessment 2 entries were not
     * altered before they were migrated, because nothing recorded them in a
     * way that could be checked. Saying so is the honest position: the trail
     * is tamper evident from the migration onwards, and the migration itself
     * is an entry in it. Claiming more would be claiming that a hash computed
     * today proves something about a file written last month.
     *
     * @return how many entries were carried over
     */
    private int migrateAuditTrail() {
        List<AuditLogEntry> ordered = new ArrayList<>(source.auditEntries());
        ordered.sort(Comparator.comparing(AuditLogEntry::getTimestamp,
                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));

        int moved = 0;
        for (AuditLogEntry entry : ordered) {
            if (entry == null) {
                continue;
            }
            auditTrail.append(entry);
            moved++;
        }
        return moved;
    }
}
