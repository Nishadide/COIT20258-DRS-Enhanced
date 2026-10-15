package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.ReportStatus;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.Severity;
import com.cqu.drs.model.StatusUpdate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores disaster reports in MySQL.
 *
 * A disaster report is an aggregate: it owns its assessment, its response
 * tasks, each task's history of status updates, and its alerts. None of those
 * mean anything apart from the report, so they are loaded and saved with it
 * rather than separately.
 *
 * <h2>Rebuilding a report from rows</h2>
 * The domain model will not let a report be put into an arbitrary state. There
 * is no setter for status; a report moves from SUBMITTED to ASSESSED only by
 * being given an assessment, to DISPATCHED only by being given work, and to
 * RESOLVED only once that work is finished. The JSON version never met this
 * problem because Gson writes fields by reflection and bypasses the model
 * entirely.
 *
 * <p>Rather than work around it, this class replays the transitions: it builds
 * the report, attaches the assessment, adds the tasks, and resolves it if the
 * stored row says it was resolved. Then it checks that the status it arrived
 * at equals the status the database holds, and refuses to return the report if
 * they differ.
 *
 * <p>That check is worth more than it costs. It means the data tier cannot
 * produce a report in a state the domain says is unreachable, and a row that
 * has drifted out of step, whether through a defect or through someone editing
 * the database by hand, is reported at the point it is read instead of
 * causing something inexplicable later.
 *
 * <h2>Why loading does not issue one query per report</h2>
 * Reading a hundred reports by fetching each report's assessment, tasks,
 * updates and alerts one report at a time would be four hundred round trips.
 * Instead five queries are issued, one per table, and the results are
 * assembled in memory. The work is the same; the number of times the server
 * waits for the database is not.
 */
public final class MySqlDisasterReportRepository implements DisasterReportRepository {

    // ---- reading -------------------------------------------------------

    private static final String SELECT_REPORTS = """
            SELECT r.reference_number, r.type, r.address, r.latitude, r.longitude,
                   r.description, r.reported_severity, r.reported_at, r.status,
                   r.reporter_id, p.name AS reporter_name,
                   p.contact_number AS reporter_contact, p.registered AS reporter_registered
              FROM disaster_reports r
              LEFT JOIN reporters p ON p.reporter_id = r.reporter_id""";

    private static final String SELECT_ASSESSMENTS = """
            SELECT assessment_id, reference_number, severity, notes, assessed_at, assessed_by
              FROM assessments""";

    private static final String SELECT_TASKS = """
            SELECT task_id, reference_number, department_id, department_name,
                   dispatched_at, status, notified
              FROM response_tasks""";

    private static final String SELECT_UPDATES = """
            SELECT u.update_id, u.task_id, u.status, u.note, u.updated_at, u.updated_by,
                   t.reference_number
              FROM status_updates u
              JOIN response_tasks t ON t.task_id = u.task_id""";

    private static final String SELECT_ALERTS = """
            SELECT alert_id, reference_number, type, affected_area, message,
                   issued_at, delivered, issued_by
              FROM alerts
             WHERE reference_number IS NOT NULL""";

    private static final String COUNT = "SELECT COUNT(*) FROM disaster_reports";

    private static final String EXISTS =
            "SELECT 1 FROM disaster_reports WHERE reference_number = ?";

    // ---- writing -------------------------------------------------------

    private static final String INSERT_REPORT = """
            INSERT INTO disaster_reports
              (reference_number, type, address, latitude, longitude, description,
               reported_severity, reported_at, status, reporter_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String UPDATE_REPORT = """
            UPDATE disaster_reports
               SET type = ?, address = ?, latitude = ?, longitude = ?,
                   description = ?, reported_severity = ?, reported_at = ?,
                   status = ?, reporter_id = ?
             WHERE reference_number = ?""";

    // The children are upserted. Each is identified by an identifier the
    // application generated, so storing one twice is the same write, not a
    // second record. This matters because AlertService stores an alert through
    // the report and through the alert repository, and both must be able to
    // run without the second one failing or duplicating.
    //
    // VALUES() is used rather than the row alias form introduced in MySQL
    // 8.0.19. The alias form is the newer spelling, but it is not understood
    // by MariaDB, which is what a teammate running XAMPP would have. VALUES()
    // is deprecated in MySQL yet works on both, and portability is worth more
    // here than using the newest syntax.
    private static final String UPSERT_REPORTER = """
            INSERT INTO reporters (reporter_id, name, contact_number, registered)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name),
                                    contact_number = VALUES(contact_number),
                                    registered = VALUES(registered)""";

    private static final String UPSERT_ASSESSMENT = """
            INSERT INTO assessments
              (assessment_id, reference_number, severity, notes, assessed_at, assessed_by)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE severity = VALUES(severity),
                                    notes = VALUES(notes),
                                    assessed_at = VALUES(assessed_at),
                                    assessed_by = VALUES(assessed_by)""";

    private static final String UPSERT_TASK = """
            INSERT INTO response_tasks
              (task_id, reference_number, department_id, department_name,
               dispatched_at, status, notified)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE department_name = VALUES(department_name),
                                    dispatched_at = VALUES(dispatched_at),
                                    status = VALUES(status),
                                    notified = VALUES(notified)""";

    private static final String UPSERT_UPDATE = """
            INSERT INTO status_updates
              (update_id, task_id, status, note, updated_at, updated_by)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE status = VALUES(status),
                                    note = VALUES(note),
                                    updated_at = VALUES(updated_at),
                                    updated_by = VALUES(updated_by)""";

    private static final String UPSERT_ALERT = """
            INSERT INTO alerts
              (alert_id, reference_number, type, affected_area, message,
               issued_at, delivered, issued_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE reference_number = VALUES(reference_number),
                                    type = VALUES(type),
                                    affected_area = VALUES(affected_area),
                                    message = VALUES(message),
                                    issued_at = VALUES(issued_at),
                                    delivered = VALUES(delivered),
                                    issued_by = VALUES(issued_by)""";

    /** MySQL's error number for a row that breaks a unique or primary key. */
    private static final int DUPLICATE_KEY = 1062;

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlDisasterReportRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    @Override
    public List<DisasterReport> findAll() {
        try (Connection connection = connections.open()) {
            return new ArrayList<>(load(connection, null).values());
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Disaster reports could not be read. " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<DisasterReport> findByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = connections.open()) {
            return Optional.ofNullable(load(connection, reference).get(reference));
        } catch (SQLException e) {
            throw new IllegalStateException("The disaster report " + reference
                    + " could not be read. " + e.getMessage(), e);
        }
    }

    @Override
    public int count() {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(COUNT)) {
            return results.next() ? results.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Disaster reports could not be counted. " + e.getMessage(), e);
        }
    }

    /**
     * Stores a report and everything it owns, in one transaction.
     *
     * Dispatching work to four departments writes the report's status, four
     * task rows and their updates. A failure part way through must not leave a
     * report marked as dispatched with two of its four tasks present, so
     * either all of it is written or none of it is.
     *
     * @param report the report to store
     * @throws DuplicateReferenceException when another report already holds
     *         this reference
     */
    @Override
    public void save(DisasterReport report) {
        store(report, false);
    }

    /**
     * {@inheritDoc}
     *
     * The insert is attempted without first asking whether the reference is
     * taken. Asking would not help: another client could claim it between the
     * question and the answer, and the window is exactly the one this method
     * exists to close. The primary key is what decides, and it decides at the
     * moment of writing.
     */
    @Override
    public void insert(DisasterReport report) {
        store(report, true);
    }

    private void store(DisasterReport report, boolean mustBeNew) {
        if (report == null) {
            throw new IllegalArgumentException("Cannot save a null report.");
        }

        Connection connection = null;
        try {
            connection = connections.openTransaction();

            if (report.getReporter() != null && !report.getReporter().isAnonymous()) {
                upsertReporter(connection, report.getReporter());
            }

            writeReport(connection, report, mustBeNew);

            if (report.getAssessment() != null) {
                upsertAssessment(connection, report.getReferenceNumber(),
                        report.getAssessment());
            }
            for (ResponseTask task : report.getTasks()) {
                upsertTask(connection, report.getReferenceNumber(), task);
                for (StatusUpdate update : task.getHistory()) {
                    upsertUpdate(connection, task.getTaskId(), update);
                }
            }
            for (Alert alert : report.getAlerts()) {
                upsertAlert(connection, report.getReferenceNumber(), alert);
            }

            connection.commit();

        } catch (SQLException e) {
            rollback(connection);
            throw new IllegalStateException("The disaster report "
                    + report.getReferenceNumber() + " could not be stored. "
                    + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    // ---- writing helpers -----------------------------------------------

    /**
     * Writes the report row, either as a new row or as a change to one that
     * exists.
     *
     * An upsert would have been shorter, and wrong, because it cannot tell a
     * change to an existing report from a second report claiming the same
     * reference. The caller says which it means, and that is why the
     * repository has both save and insert.
     *
     * @param connection the transaction
     * @param report     the report to store
     * @param mustBeNew  true when the report must not already exist
     * @throws SQLException when the statement fails for any other reason
     */
    private void writeReport(Connection connection, DisasterReport report,
                             boolean mustBeNew) throws SQLException {
        boolean alreadyStored = !mustBeNew && exists(connection, report.getReferenceNumber());

        String sql = alreadyStored ? UPDATE_REPORT : INSERT_REPORT;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            if (!alreadyStored) {
                statement.setString(i++, report.getReferenceNumber());
            }
            statement.setString(i++, report.getType().name());
            statement.setString(i++, report.getLocation().getAddress());
            setDouble(statement, i++, report.getLocation().getLatitude());
            setDouble(statement, i++, report.getLocation().getLongitude());
            statement.setString(i++, report.getDescription());
            statement.setString(i++, report.getReportedSeverity() == null
                    ? null : report.getReportedSeverity().name());
            setTimestamp(statement, i++, report.getReportedAt());
            statement.setString(i++, report.currentStatus().name());
            statement.setString(i++, report.getReporter() == null
                    || report.getReporter().isAnonymous()
                    ? null : report.getReporter().getReporterId());
            if (alreadyStored) {
                statement.setString(i, report.getReferenceNumber());
            }

            try {
                statement.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException e) {
                // Narrowed to a duplicate key. The same exception type is
                // raised by a check constraint and by a foreign key, and
                // reporting either of those as a reference clash would send
                // the caller into a retry loop over a fault that retrying
                // cannot fix.
                if (e.getErrorCode() == DUPLICATE_KEY) {
                    throw new DuplicateReferenceException(report.getReferenceNumber(), e);
                }
                throw e;
            }
        }
    }

    private boolean exists(Connection connection, String reference) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS)) {
            statement.setString(1, reference);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private void upsertReporter(Connection connection, Reporter reporter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_REPORTER)) {
            statement.setString(1, reporter.getReporterId());
            statement.setString(2, reporter.getName());
            statement.setString(3, reporter.getContactNumber());
            statement.setBoolean(4, reporter.isRegistered());
            statement.executeUpdate();
        }
    }

    private void upsertAssessment(Connection connection, String reference,
                                  Assessment assessment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ASSESSMENT)) {
            statement.setString(1, assessment.getAssessmentId());
            statement.setString(2, reference);
            statement.setString(3, assessment.getSeverity().name());
            statement.setString(4, assessment.getNotes());
            setTimestamp(statement, 5, assessment.getAssessedAt());
            statement.setString(6, assessment.getAssessedBy());
            statement.executeUpdate();
        }
    }

    private void upsertTask(Connection connection, String reference, ResponseTask task)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_TASK)) {
            statement.setString(1, task.getTaskId());
            statement.setString(2, reference);
            statement.setString(3, task.getDepartmentId());
            statement.setString(4, task.getDepartmentName());
            setTimestamp(statement, 5, task.getDispatchedAt());
            statement.setString(6, task.getStatus().name());
            statement.setBoolean(7, task.isNotified());
            statement.executeUpdate();
        }
    }

    private void upsertUpdate(Connection connection, String taskId, StatusUpdate update)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_UPDATE)) {
            statement.setString(1, update.getUpdateId());
            statement.setString(2, taskId);
            statement.setString(3, update.getStatus().name());
            statement.setString(4, update.getNote());
            setTimestamp(statement, 5, update.getUpdatedAt());
            statement.setString(6, update.getUpdatedBy());
            statement.executeUpdate();
        }
    }

    private void upsertAlert(Connection connection, String reference, Alert alert)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ALERT)) {
            statement.setString(1, alert.getAlertId());
            statement.setString(2, reference);
            statement.setString(3, alert.getType().name());
            statement.setString(4, alert.getAffectedArea());
            statement.setString(5, alert.getMessage());
            setTimestamp(statement, 6, alert.getIssuedAt());
            statement.setBoolean(7, alert.isDelivered());
            statement.setString(8, alert.getIssuedBy());
            statement.executeUpdate();
        }
    }

    // ---- reading helpers -----------------------------------------------

    /**
     * Loads reports and everything they own.
     *
     * @param connection the connection to read on
     * @param reference  one reference to load, or null for every report
     * @return the reports, keyed by reference, in the order read
     * @throws SQLException when a query fails
     */
    private Map<String, DisasterReport> load(Connection connection, String reference)
            throws SQLException {

        Map<String, DisasterReport> reports = new LinkedHashMap<>();
        Map<String, ReportStatus> storedStatus = new LinkedHashMap<>();
        readReports(connection, reference, reports, storedStatus);

        if (reports.isEmpty()) {
            return reports;
        }

        // Order matters. A report will not accept work before it has been
        // assessed, so the assessments have to be attached first.
        readAssessments(connection, reference, reports);

        Map<String, ResponseTask> tasksById = new LinkedHashMap<>();
        Map<String, ResponseStatus> storedTaskStatus = new LinkedHashMap<>();
        readTasks(connection, reference, reports, tasksById, storedTaskStatus);
        readUpdates(connection, reference, tasksById);
        verifyTaskStatus(tasksById, storedTaskStatus);

        readAlerts(connection, reference, reports);

        applyStoredStatus(reports, storedStatus);

        return reports;
    }

    private void readReports(Connection connection, String reference,
                             Map<String, DisasterReport> reports,
                             Map<String, ReportStatus> storedStatus) throws SQLException {
        String sql = SELECT_REPORTS
                + (reference == null ? " ORDER BY r.reported_at"
                                     : " WHERE r.reference_number = ?");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (reference != null) {
                statement.setString(1, reference);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String key = results.getString("reference_number");

                    Location location = new Location(
                            results.getString("address"),
                            readDouble(results, "latitude"),
                            readDouble(results, "longitude"));

                    DisasterReport report = new DisasterReport(
                            key,
                            DisasterType.valueOf(results.getString("type")),
                            location,
                            readEnum(results, "reported_severity", Severity.class),
                            results.getString("description"),
                            readReporter(results));

                    // The constructor stamps the current time, which is right
                    // for a new report and wrong for one being read back.
                    report.setReportedAt(results.getObject("reported_at",
                            LocalDateTime.class));

                    reports.put(key, report);
                    storedStatus.put(key,
                            ReportStatus.valueOf(results.getString("status")));
                }
            }
        }
    }

    private void readAssessments(Connection connection, String reference,
                                 Map<String, DisasterReport> reports) throws SQLException {
        String sql = SELECT_ASSESSMENTS
                + (reference == null ? "" : " WHERE reference_number = ?");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (reference != null) {
                statement.setString(1, reference);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    DisasterReport report = reports.get(results.getString("reference_number"));
                    if (report == null) {
                        continue;
                    }
                    Assessment assessment = new Assessment(
                            results.getString("assessment_id"),
                            Severity.valueOf(results.getString("severity")),
                            results.getString("notes"),
                            results.getString("assessed_by"));
                    assessment.setAssessedAt(
                            results.getObject("assessed_at", LocalDateTime.class));

                    report.attachAssessment(assessment);
                }
            }
        }
    }

    private void readTasks(Connection connection, String reference,
                           Map<String, DisasterReport> reports,
                           Map<String, ResponseTask> tasksById,
                           Map<String, ResponseStatus> storedTaskStatus) throws SQLException {
        String sql = SELECT_TASKS
                + (reference == null ? " ORDER BY dispatched_at"
                                     : " WHERE reference_number = ? ORDER BY dispatched_at");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (reference != null) {
                statement.setString(1, reference);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String key = results.getString("reference_number");
                    DisasterReport report = reports.get(key);
                    if (report == null) {
                        continue;
                    }

                    // ResponseTask is built from a Department because that is
                    // the only public way to make one. It is not a stand-in:
                    // the identifier and the name are the two things the task
                    // recorded at dispatch, and they are exactly what is read
                    // back. The contact address was never part of a task.
                    ResponseTask task = new ResponseTask(
                            results.getString("task_id"),
                            new Department(results.getString("department_id"),
                                    results.getString("department_name"), null));
                    task.setDispatchedAt(
                            results.getObject("dispatched_at", LocalDateTime.class));
                    task.setNotified(results.getBoolean("notified"));

                    // The task is deliberately left in the DISPATCHED state
                    // its constructor gives it. Its real status is restored by
                    // replaying its history, for the same reason the report's
                    // is: the history is the record of what happened, and the
                    // status column is a summary of it. Setting the column
                    // value here would mean a finished task refused its own
                    // first update, because a complete task cannot be updated.
                    storedTaskStatus.put(results.getString("task_id"),
                            ResponseStatus.valueOf(results.getString("status")));

                    try {
                        report.addTask(task);
                    } catch (IllegalStateException e) {
                        throw new IllegalStateException("The report " + key
                                + " holds response task " + task.getTaskId()
                                + " but the model will not accept it: " + e.getMessage()
                                + " This means the stored rows describe a report that "
                                + "could not have been produced by the application.", e);
                    }
                    tasksById.put(task.getTaskId(), task);
                }
            }
        }
    }

    private void readUpdates(Connection connection, String reference,
                             Map<String, ResponseTask> tasksById) throws SQLException {
        String sql = SELECT_UPDATES
                + (reference == null ? " ORDER BY u.updated_at"
                                     : " WHERE t.reference_number = ? ORDER BY u.updated_at");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (reference != null) {
                statement.setString(1, reference);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    ResponseTask task = tasksById.get(results.getString("task_id"));
                    if (task == null) {
                        continue;
                    }

                    StatusUpdate update = new StatusUpdate(
                            results.getString("update_id"),
                            ResponseStatus.valueOf(results.getString("status")),
                            results.getString("note"),
                            results.getString("updated_by"));
                    update.setUpdatedAt(
                            results.getObject("updated_at", LocalDateTime.class));

                    // addUpdate sets the task's status from the update, so
                    // replaying the history in order restores the status the
                    // task actually reached. Updates are read oldest first,
                    // which is what makes that work.
                    try {
                        task.addUpdate(update);
                    } catch (IllegalStateException e) {
                        throw new IllegalStateException("The status history of task "
                                + task.getTaskId() + " could not be replayed: "
                                + e.getMessage()
                                + " An update recorded after the task was already "
                                + "complete means the stored rows are inconsistent.", e);
                    }
                }
            }
        }
    }

    private void readAlerts(Connection connection, String reference,
                            Map<String, DisasterReport> reports) throws SQLException {
        String sql = SELECT_ALERTS
                + (reference == null ? " ORDER BY issued_at"
                                     : " AND reference_number = ? ORDER BY issued_at");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (reference != null) {
                statement.setString(1, reference);
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    DisasterReport report = reports.get(results.getString("reference_number"));
                    if (report == null) {
                        continue;
                    }
                    Alert alert = new Alert(
                            results.getString("alert_id"),
                            AlertType.valueOf(results.getString("type")),
                            results.getString("affected_area"),
                            results.getString("message"),
                            results.getString("issued_by"));
                    alert.setIssuedAt(results.getObject("issued_at", LocalDateTime.class));
                    alert.setDelivered(results.getBoolean("delivered"));

                    report.addAlert(alert);
                }
            }
        }
    }

    /**
     * Checks each task's replayed status against the status column.
     *
     * A task with no history is still DISPATCHED, which is what the column
     * should say for work that has not been touched since it was sent out. A
     * task whose column says otherwise while holding no history describes
     * something the application cannot produce, because every status change
     * goes through an update.
     *
     * @param tasksById        the tasks rebuilt
     * @param storedTaskStatus what the status column said for each
     */
    private void verifyTaskStatus(Map<String, ResponseTask> tasksById,
                                  Map<String, ResponseStatus> storedTaskStatus) {
        for (Map.Entry<String, ResponseTask> entry : tasksById.entrySet()) {
            ResponseStatus replayed = entry.getValue().getStatus();
            ResponseStatus stored = storedTaskStatus.get(entry.getKey());

            if (replayed != stored) {
                throw new IllegalStateException("Response task " + entry.getKey()
                        + " is stored with status " + stored
                        + ", but replaying its " + entry.getValue().getHistory().size()
                        + " status updates leaves it " + replayed
                        + ". The stored rows are inconsistent.");
            }
        }
    }

    /**
     * Finishes the replay and checks the result against the stored status.
     *
     * Attaching an assessment and adding tasks has already moved each report
     * as far as ASSESSED or DISPATCHED. Only RESOLVED remains, and it is the
     * one transition with no artefact of its own to drive it.
     *
     * @param reports      the reports rebuilt
     * @param storedStatus what the status column said for each
     */
    private void applyStoredStatus(Map<String, DisasterReport> reports,
                                   Map<String, ReportStatus> storedStatus) {
        for (Map.Entry<String, DisasterReport> entry : reports.entrySet()) {
            DisasterReport report = entry.getValue();
            ReportStatus stored = storedStatus.get(entry.getKey());

            if (stored == ReportStatus.RESOLVED
                    && report.currentStatus() != ReportStatus.RESOLVED) {
                try {
                    report.resolve();
                } catch (IllegalStateException e) {
                    throw new IllegalStateException("The report " + entry.getKey()
                            + " is stored as resolved but cannot be resolved: "
                            + e.getMessage(), e);
                }
            }

            if (report.currentStatus() != stored) {
                // Reached only when the rows disagree with themselves, such as
                // a report stored as DISPATCHED with no response tasks. Saying
                // so here is far better than returning a report whose status
                // is quietly wrong.
                throw new IllegalStateException("The report " + entry.getKey()
                        + " is stored with status " + stored
                        + ", but the records it holds describe a report that is "
                        + report.currentStatus()
                        + ". The stored rows are inconsistent.");
            }
        }
    }

    private static Reporter readReporter(ResultSet results) throws SQLException {
        String reporterId = results.getString("reporter_id");
        if (reporterId == null) {
            // No reporter row means the report was lodged anonymously, which
            // the case study allows.
            return null;
        }
        Reporter reporter = new Reporter(reporterId,
                results.getString("reporter_name"),
                results.getString("reporter_contact"));
        reporter.setRegistered(results.getBoolean("reporter_registered"));
        return reporter;
    }

    // ---- small helpers -------------------------------------------------

    private static <E extends Enum<E>> E readEnum(ResultSet results, String column,
                                                  Class<E> type) throws SQLException {
        String value = results.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static Double readDouble(ResultSet results, String column) throws SQLException {
        BigDecimal value = results.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private static void setDouble(PreparedStatement statement, int index, Double value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, BigDecimal.valueOf(value));
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index,
                                     LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, value);
        }
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            System.err.println("The transaction could not be rolled back: " + e.getMessage());
        }
    }

    private static void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            // Returned to auto-commit before closing, so that a pooled
            // connection is not handed to the next caller mid-transaction.
            connection.setAutoCommit(true);
            connection.close();
        } catch (SQLException e) {
            System.err.println("The connection did not close cleanly: " + e.getMessage());
        }
    }
}
