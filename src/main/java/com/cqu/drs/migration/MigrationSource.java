package com.cqu.drs.migration;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AuditLogEntry;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.User;

import java.util.List;

/**
 * Where the data being migrated comes from.
 *
 * It is an interface with one implementation because the migration should not
 * depend on the JSON files being the source. If a later version of the system
 * has to be migrated from somewhere else, the migration itself does not
 * change, and in a test the source can be a handful of objects built in the
 * test method rather than files on a disk.
 */
public interface MigrationSource {

    /**
     * Where this data came from, for the log and the audit entry.
     *
     * @return a short description, such as the folder being read
     */
    String describe();

    /**
     * The departments on record.
     *
     * @return the departments, never null
     */
    List<Department> departments();

    /**
     * The staff accounts, with their stored password hashes.
     *
     * @return the accounts, never null
     */
    List<User> accounts();

    /**
     * The disaster reports, each with its reporter, assessment, tasks, status
     * updates and alerts.
     *
     * @return the reports, never null
     */
    List<DisasterReport> reports();

    /**
     * The alerts issued.
     *
     * @return the alerts, never null
     */
    List<Alert> alerts();

    /**
     * The audit trail.
     *
     * @return the entries, never null
     */
    List<AuditLogEntry> auditEntries();
}
