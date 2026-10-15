package com.cqu.drs.migration;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.User;
import com.cqu.drs.repository.JsonAlertRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonDepartmentRepository;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import com.cqu.drs.repository.JsonUserRepository;

import java.nio.file.Path;
import java.util.List;

/**
 * The Assessment 2 data, read from the JSON files the desktop application
 * wrote.
 *
 * <h2>It reads through the Assessment 2 repositories</h2>
 * Not by parsing the files here. Those repositories already know the file
 * names, the list types and the adapters that rebuild an abstract user or a
 * timestamp, and they are covered by Assessment 2's own tests. Reading the
 * files again here would be a second implementation of something already
 * working, and the two would eventually disagree about a format neither of
 * them owns.
 *
 * <p>A file that is not there reads as an empty list rather than an error,
 * which is the behaviour the stores were given so that the application could
 * start the first time it was run. It serves this just as well: a folder with
 * no reports in it migrates no reports, and says so, instead of failing.
 *
 * <p>Nothing here writes. The source of a migration is read only by
 * intention: whatever happens to the destination, the Assessment 2 data is
 * still there afterwards, which matters because it is also a submitted
 * artefact.
 */
public final class JsonMigrationSource implements MigrationSource {

    private final Path dataDirectory;
    private final JsonDepartmentRepository departments;
    private final JsonUserRepository accounts;
    private final JsonDisasterReportRepository reports;
    private final JsonAlertRepository alerts;
    private final JsonAuditRepository auditTrail;

    /**
     * Creates the source.
     *
     * @param dataDirectory the folder holding reports.json, users.json and the
     *                      rest
     */
    public JsonMigrationSource(Path dataDirectory) {
        if (dataDirectory == null) {
            throw new IllegalArgumentException("A migration source needs a folder.");
        }
        this.dataDirectory = dataDirectory;

        JsonDataStore store = new JsonDataStore(dataDirectory);
        this.departments = new JsonDepartmentRepository(store);
        this.accounts = new JsonUserRepository(store);
        this.reports = new JsonDisasterReportRepository(store);
        this.alerts = new JsonAlertRepository(store);
        this.auditTrail = new JsonAuditRepository(store);
    }

    @Override
    public String describe() {
        return "the JSON files in " + dataDirectory.toAbsolutePath();
    }

    @Override
    public List<Department> departments() {
        // findAll, not seedIfEmpty. Seeding would write the eight standard
        // departments into the source folder, and a migration must not alter
        // what it is reading.
        return departments.findAll();
    }

    @Override
    public List<User> accounts() {
        return accounts.findAll();
    }

    @Override
    public List<DisasterReport> reports() {
        return reports.findAll();
    }

    @Override
    public List<Alert> alerts() {
        return alerts.findAll();
    }

    @Override
    public List<AuditLogEntry> auditEntries() {
        return auditTrail.findAll();
    }
}
