package com.cqu.drs.db;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The tables carried over from the Assessment 2 domain model, plus the two
 * tables the client-server architecture adds.
 *
 * Design decisions recorded here rather than only in the report, so that the
 * reason for a column is readable next to the column itself.
 *
 * <h2>Why InnoDB</h2>
 * InnoDB is the only MySQL engine that supports both foreign keys and
 * transactions. The resource allocation feature depends on row level locking
 * inside a transaction, so no other engine would do.
 *
 * <h2>Why utf8mb4 with the 0900_ai_ci collation</h2>
 * utf8mb4 stores the whole of Unicode, including characters outside the basic
 * multilingual plane, which utf8mb3 cannot. The collation is accent and case
 * insensitive, which matters for one specific inherited behaviour: Assessment
 * 2 matches a username without regard to case, and test T46 variant
 * {@code loginIgnoresUsernameCase} proves it. A case insensitive collation
 * means the unique index on username enforces exactly that rule, so the
 * database and the Java code agree rather than differ.
 *
 * <h2>Why DATETIME(6) rather than TIMESTAMP</h2>
 * The domain uses {@code LocalDateTime}, which carries no time zone. MySQL
 * TIMESTAMP converts to and from UTC using the session time zone, so a value
 * written on one machine and read on another with a different time zone comes
 * back changed. DATETIME stores what it is given. The (6) keeps microsecond
 * precision, which {@code LocalDateTime.now()} produces and which the audit
 * trail needs in order to order two actions in the same second.
 *
 * <h2>Why no foreign key uses ON UPDATE CASCADE</h2>
 * Every foreign key here declares ON UPDATE RESTRICT. No primary key in this
 * schema is ever updated: they are generated identifiers, department codes
 * fixed at seeding, and report references of the form DR-0001. A cascade on
 * update is therefore machinery for a case that cannot arise, and RESTRICT
 * additionally refuses an attempt to change an identifier rather than quietly
 * rewriting every row that refers to it.
 *
 * <p>There is a second reason, found by executing this schema rather than by
 * reading it. A check constraint that refers to a column covered by a foreign
 * key is rejected outright when that foreign key can alter the column, which
 * ON UPDATE CASCADE and ON DELETE SET NULL both can. The engine will not
 * accept a constraint it would be unable to re-evaluate after a referential
 * action changed the value. The users table needs exactly that combination,
 * because chk_users_responder_has_department refers to department_id, which is
 * also a foreign key. Declaring ON UPDATE RESTRICT keeps the constraint and
 * loses nothing, since the cascade was never going to fire.
 *
 * <p>ON DELETE CASCADE is unaffected and is kept where it is wanted, because
 * deleting the parent removes the child row entirely rather than leaving a row
 * whose check would need testing again.
 *
 * <h2>Why DECIMAL for coordinates rather than DOUBLE</h2>
 * DECIMAL(8,6) and DECIMAL(9,6) hold the full latitude and longitude ranges
 * exactly, to six decimal places, which is a resolution of about 0.11 metres.
 * DOUBLE is a binary approximation, so a coordinate written and read back can
 * differ in its last digits. The distance calculation is done in Java, so
 * nothing is lost by storing the values exactly.
 */
public final class CoreSchema implements SchemaModule {

    /**
     * Applied to every table. Repeated through a constant rather than typed
     * out eleven times, so that no table can accidentally be created with a
     * different engine or character set.
     */
    private static final String TABLE_OPTIONS =
            " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";

    /**
     * Records that a one-off action has been carried out, so it is not carried
     * out twice.
     *
     * The migration of the Assessment 2 JSON data writes a row here when it
     * finishes. Running the migration again then reports that it has already
     * run instead of attempting the work a second time.
     */
    private static final String SCHEMA_METADATA = """
            CREATE TABLE IF NOT EXISTS schema_metadata (
              item        VARCHAR(50)  NOT NULL,
              applied_at  DATETIME(6)  NOT NULL,
              detail      VARCHAR(255) NULL,
              PRIMARY KEY (item)
            )""";

    /**
     * The responding organisations. No foreign keys, so this is created first.
     *
     * The rows are not inserted here. Reference data is seeded by
     * MySqlDepartmentRepository.seedIfEmpty, which is the same arrangement
     * Assessment 2 used, so the behaviour is unchanged by the move to a
     * database and the inherited tests for it still describe what happens.
     */
    private static final String DEPARTMENTS = """
            CREATE TABLE IF NOT EXISTS departments (
              department_id  VARCHAR(20)  NOT NULL,
              name           VARCHAR(100) NOT NULL,
              contact_email  VARCHAR(150) NOT NULL,
              available      BOOLEAN      NOT NULL DEFAULT TRUE,
              PRIMARY KEY (department_id)
            )""";

    /**
     * All three kinds of user in one table.
     *
     * The User hierarchy is mapped with single table inheritance, with the
     * role column as the discriminator. The alternative, a table per subclass,
     * would add three tables in order to store two nullable columns, and would
     * turn every user lookup into a join or a union. The two subclass columns
     * are department_id, held by DepartmentResponder, and coordinator_id, held
     * by EmergencyCoordinator. SystemAdministrator adds no state at all.
     *
     * The cost of single table inheritance is that a column meaningful to one
     * subclass must be nullable for the others, which weakens the schema's
     * ability to enforce that a responder has a department. The last check
     * constraint recovers that: it permits a null department only when the role
     * is not RESPONDER, which is the same rule AccountService enforces in Java.
     * The rule is therefore enforced at two tiers, and a row written by any
     * route other than the application still cannot break it.
     *
     * password_salt and password_algorithm are present from the start although
     * nothing writes them yet. Assessment 2 stored unsalted SHA-256 digests,
     * and DRS-Enhanced replaces that with salted PBKDF2. Including the columns
     * now means the change of algorithm needs no change of schema, and a
     * digest migrated from Assessment 2 can be recognised by its algorithm
     * column and upgraded the next time its owner signs in.
     */
    private static final String USERS = """
            CREATE TABLE IF NOT EXISTS users (
              user_id               VARCHAR(40)  NOT NULL,
              username              VARCHAR(20)  NOT NULL,
              password_hash         VARCHAR(255) NOT NULL,
              password_salt         VARCHAR(64)  NULL,
              password_algorithm    VARCHAR(20)  NOT NULL DEFAULT 'SHA-256',
              full_name             VARCHAR(100) NOT NULL,
              role                  VARCHAR(20)  NOT NULL,
              active                BOOLEAN      NOT NULL DEFAULT TRUE,
              must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
              department_id         VARCHAR(20)  NULL,
              coordinator_id        VARCHAR(20)  NULL,
              PRIMARY KEY (user_id),
              UNIQUE KEY uq_users_username (username),
              CONSTRAINT fk_users_department
                FOREIGN KEY (department_id) REFERENCES departments (department_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
              CONSTRAINT chk_users_role
                CHECK (role IN ('COORDINATOR', 'RESPONDER', 'ADMINISTRATOR')),
              CONSTRAINT chk_users_username_length
                CHECK (CHAR_LENGTH(username) BETWEEN 3 AND 20),
              CONSTRAINT chk_users_responder_has_department
                CHECK (role <> 'RESPONDER' OR department_id IS NOT NULL)
            )""";

    /**
     * People who lodge reports. Separate from users because reporting is open
     * to the public and a reporter has no account.
     *
     * contact_number is declared VARCHAR(255) although it holds at most 15
     * digits. The value is encrypted at rest, and the stored form is the
     * initialisation vector and the ciphertext, both base64 encoded and
     * separated by a colon, which is longer than the plaintext. A column sized
     * for the plaintext would truncate the ciphertext and the value would
     * never decrypt.
     */
    private static final String REPORTERS = """
            CREATE TABLE IF NOT EXISTS reporters (
              reporter_id     VARCHAR(40)  NOT NULL,
              name            VARCHAR(100) NULL,
              contact_number  VARCHAR(255) NULL,
              registered      BOOLEAN      NOT NULL DEFAULT FALSE,
              PRIMARY KEY (reporter_id)
            )""";

    /**
     * The reported disasters, and the root of everything recorded about one.
     *
     * The reporter is nullable because the case study allows an anonymous
     * report. The foreign key uses ON DELETE SET NULL rather than CASCADE: if
     * a reporter record were ever removed, the disaster must survive, because
     * the response to it does not depend on knowing who reported it.
     *
     * The check constraints on latitude and longitude repeat the bounds held
     * in DrsConstants. MySQL has enforced check constraints since 8.0.16, so
     * these are real constraints and not documentation. Before that version
     * MySQL parsed them and silently ignored them, which is worth knowing when
     * reading older examples.
     */
    private static final String DISASTER_REPORTS = """
            CREATE TABLE IF NOT EXISTS disaster_reports (
              reference_number   VARCHAR(12)  NOT NULL,
              type               VARCHAR(20)  NOT NULL,
              address            VARCHAR(200) NOT NULL,
              latitude           DECIMAL(8,6) NULL,
              longitude          DECIMAL(9,6) NULL,
              description        VARCHAR(500) NULL,
              reported_severity  VARCHAR(10)  NULL,
              reported_at        DATETIME(6)  NOT NULL,
              status             VARCHAR(12)  NOT NULL,
              reporter_id        VARCHAR(40)  NULL,
              PRIMARY KEY (reference_number),
              KEY idx_reports_status (status),
              KEY idx_reports_reported_at (reported_at),
              CONSTRAINT fk_reports_reporter
                FOREIGN KEY (reporter_id) REFERENCES reporters (reporter_id)
                ON DELETE SET NULL ON UPDATE RESTRICT,
              CONSTRAINT chk_reports_reference_format
                CHECK (reference_number REGEXP '^DR-[0-9]{4,}$'),
              CONSTRAINT chk_reports_type
                CHECK (type IN ('HURRICANE', 'FIRE', 'EARTHQUAKE', 'FLOOD', 'OTHER')),
              CONSTRAINT chk_reports_status
                CHECK (status IN ('SUBMITTED', 'ASSESSED', 'DISPATCHED', 'RESOLVED')),
              CONSTRAINT chk_reports_severity
                CHECK (reported_severity IS NULL
                       OR reported_severity IN ('SEVERE', 'HIGH', 'MEDIUM', 'LOW')),
              CONSTRAINT chk_reports_latitude
                CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
              CONSTRAINT chk_reports_longitude
                CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
              CONSTRAINT chk_reports_coordinates_paired
                CHECK ((latitude IS NULL AND longitude IS NULL)
                       OR (latitude IS NOT NULL AND longitude IS NOT NULL))
            )""";

    /**
     * A coordinator's judgement of a disaster.
     *
     * The class diagram gives a report zero or one assessment. The unique key
     * on reference_number is what enforces that multiplicity: a second
     * assessment for the same report is rejected by the database. Without it
     * the schema would permit many assessments per report, which the design
     * does not allow.
     *
     * Priority is not stored. It is derived from the severity by
     * Assessment.derivePriority, and storing a derived value invites the two
     * to disagree. Assessment 2 made the same decision for the same reason.
     */
    private static final String ASSESSMENTS = """
            CREATE TABLE IF NOT EXISTS assessments (
              assessment_id     VARCHAR(40)  NOT NULL,
              reference_number  VARCHAR(12)  NOT NULL,
              severity          VARCHAR(10)  NOT NULL,
              notes             VARCHAR(500) NULL,
              assessed_at       DATETIME(6)  NOT NULL,
              assessed_by       VARCHAR(20)  NULL,
              PRIMARY KEY (assessment_id),
              UNIQUE KEY uq_assessments_report (reference_number),
              CONSTRAINT fk_assessments_report
                FOREIGN KEY (reference_number)
                REFERENCES disaster_reports (reference_number)
                ON DELETE CASCADE ON UPDATE RESTRICT,
              CONSTRAINT chk_assessments_severity
                CHECK (severity IN ('SEVERE', 'HIGH', 'MEDIUM', 'LOW'))
            )""";

    /**
     * Work dispatched to one department for one disaster.
     *
     * The unique key on reference_number and department_id enforces the rule
     * that a department is dispatched to a given disaster at most once. The
     * coordination screen also refuses a duplicate, but a check in a screen
     * protects only that screen, and from Assessment 3 onwards the same
     * operation can be invoked by any client that speaks the protocol.
     *
     * department_name duplicates a value available by joining departments, and
     * that is deliberate rather than an oversight. The column records the name
     * the department held when the task was dispatched. A department renamed
     * afterwards must not appear to have been renamed in the historical record
     * of a response, so the value is captured at dispatch and never updated.
     * The foreign key on department_id keeps the reference itself correct.
     */
    private static final String RESPONSE_TASKS = """
            CREATE TABLE IF NOT EXISTS response_tasks (
              task_id           VARCHAR(40)  NOT NULL,
              reference_number  VARCHAR(12)  NOT NULL,
              department_id     VARCHAR(20)  NOT NULL,
              department_name   VARCHAR(100) NOT NULL,
              dispatched_at     DATETIME(6)  NOT NULL,
              status            VARCHAR(12)  NOT NULL,
              notified          BOOLEAN      NOT NULL DEFAULT FALSE,
              PRIMARY KEY (task_id),
              UNIQUE KEY uq_tasks_report_department (reference_number, department_id),
              KEY idx_tasks_department (department_id),
              CONSTRAINT fk_tasks_report
                FOREIGN KEY (reference_number)
                REFERENCES disaster_reports (reference_number)
                ON DELETE CASCADE ON UPDATE RESTRICT,
              CONSTRAINT fk_tasks_department
                FOREIGN KEY (department_id) REFERENCES departments (department_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
              CONSTRAINT chk_tasks_status
                CHECK (status IN ('DISPATCHED', 'IN_PROGRESS', 'COMPLETE'))
            )""";

    /**
     * The history of status changes on one task.
     *
     * Rows are appended and never altered, which is what makes the history a
     * history. The index on task_id and updated_at serves the only query made
     * against this table, which is the updates for one task in the order they
     * happened.
     */
    private static final String STATUS_UPDATES = """
            CREATE TABLE IF NOT EXISTS status_updates (
              update_id   VARCHAR(40)  NOT NULL,
              task_id     VARCHAR(40)  NOT NULL,
              status      VARCHAR(12)  NOT NULL,
              note        VARCHAR(200) NULL,
              updated_at  DATETIME(6)  NOT NULL,
              updated_by  VARCHAR(20)  NULL,
              PRIMARY KEY (update_id),
              KEY idx_updates_task_time (task_id, updated_at),
              CONSTRAINT fk_updates_task
                FOREIGN KEY (task_id) REFERENCES response_tasks (task_id)
                ON DELETE CASCADE ON UPDATE RESTRICT,
              CONSTRAINT chk_updates_status
                CHECK (status IN ('DISPATCHED', 'IN_PROGRESS', 'COMPLETE'))
            )""";

    /**
     * Public warnings and evacuation instructions.
     *
     * Assessment 2 held each alert in two places, once inside the report that
     * owned it and once in a separate list of all alerts, and kept the two in
     * step by writing both. One table with a nullable foreign key replaces
     * both: the alerts for a report are a query on reference_number, and the
     * full list is a query with no condition. There is now one copy of the
     * truth, so the two copies cannot diverge.
     *
     * reference_number is nullable because the delete rule is SET NULL. An
     * alert that was broadcast to the public was broadcast, and deleting the
     * disaster record must not erase the evidence that it happened.
     */
    private static final String ALERTS = """
            CREATE TABLE IF NOT EXISTS alerts (
              alert_id          VARCHAR(40)  NOT NULL,
              reference_number  VARCHAR(12)  NULL,
              type              VARCHAR(12)  NOT NULL,
              affected_area     VARCHAR(200) NOT NULL,
              message           VARCHAR(300) NOT NULL,
              issued_at         DATETIME(6)  NOT NULL,
              delivered         BOOLEAN      NOT NULL DEFAULT FALSE,
              issued_by         VARCHAR(20)  NULL,
              PRIMARY KEY (alert_id),
              KEY idx_alerts_report (reference_number),
              CONSTRAINT fk_alerts_report
                FOREIGN KEY (reference_number)
                REFERENCES disaster_reports (reference_number)
                ON DELETE SET NULL ON UPDATE RESTRICT,
              CONSTRAINT chk_alerts_type
                CHECK (type IN ('WARNING', 'EVACUATION'))
            )""";

    /**
     * The audit trail, append only and hash chained.
     *
     * sequence_no is assigned by the database rather than the application, so
     * the order of entries is decided in one place even when several server
     * threads write at once. Two threads cannot be given the same number, and
     * the chain therefore has one unambiguous order.
     *
     * entry_hash is SHA-256 over the sequence number, actor, action, timestamp
     * and the hash of the entry before it, so each hash depends on every entry
     * before it. Altering or deleting one entry breaks the chain at that point
     * and at every point after it, which is what makes the trail tamper
     * evident. The first entry's previous_hash is sixty-four zeros.
     *
     * The timestamp is written from the server clock, never from a value sent
     * by a client, because a client clock can be wrong and an audit trail
     * ordered by client clocks establishes nothing.
     */
    private static final String AUDIT_LOG = """
            CREATE TABLE IF NOT EXISTS audit_log (
              sequence_no    BIGINT       NOT NULL AUTO_INCREMENT,
              entry_id       VARCHAR(40)  NOT NULL,
              actor          VARCHAR(50)  NOT NULL,
              action         VARCHAR(500) NOT NULL,
              timestamp      DATETIME(6)  NOT NULL,
              previous_hash  CHAR(64)     NOT NULL,
              entry_hash     CHAR(64)     NOT NULL,
              PRIMARY KEY (sequence_no),
              UNIQUE KEY uq_audit_entry (entry_id),
              KEY idx_audit_actor (actor),
              KEY idx_audit_timestamp (timestamp)
            )""";

    /**
     * Every request the server has handled, with its signature and its three
     * timestamps.
     *
     * This table has no counterpart in Assessment 2, because a single process
     * application receives no requests. It exists to make two of the required
     * security properties demonstrable rather than merely claimed.
     *
     * Time stamping: sent_at is the client's clock, received_at and
     * responded_at are the server's. Holding all three allows the delay
     * between a request being sent and handled to be measured, and allows a
     * client whose clock is wrong to be identified instead of quietly
     * corrupting the record.
     *
     * Non-repudiation: the signature stored is the HMAC the client computed
     * with the key issued to its session. It can be recomputed afterwards,
     * which means a claim that a request was never sent can be tested rather
     * than argued.
     *
     * Replay detection: the unique key on request_id refuses a request
     * identifier that has been handled before. Enforcing this in the database
     * rather than in a map in memory means the protection survives a restart
     * of the server. A client retrying after a failure must therefore use a
     * new request identifier, which is correct, because a retry is a new
     * instruction and not evidence of the first one.
     */
    private static final String REQUEST_LOG = """
            CREATE TABLE IF NOT EXISTS request_log (
              sequence_no    BIGINT       NOT NULL AUTO_INCREMENT,
              request_id     CHAR(36)     NOT NULL,
              session_token  VARCHAR(40)  NULL,
              actor          VARCHAR(20)  NULL,
              operation      VARCHAR(50)  NOT NULL,
              client_address VARCHAR(45)  NULL,
              sent_at        DATETIME(6)  NULL,
              received_at    DATETIME(6)  NOT NULL,
              responded_at   DATETIME(6)  NULL,
              status         VARCHAR(10)  NOT NULL,
              error_code     VARCHAR(30)  NULL,
              signature      CHAR(64)     NULL,
              PRIMARY KEY (sequence_no),
              UNIQUE KEY uq_request_log_request (request_id),
              KEY idx_request_log_actor (actor),
              KEY idx_request_log_received (received_at),
              CONSTRAINT chk_request_log_status CHECK (status IN ('OK', 'ERROR'))
            )""";

    @Override
    public String name() {
        return "core";
    }

    /**
     * The core tables in dependency order.
     *
     * The order is not cosmetic. MySQL rejects a foreign key pointing at a
     * table that does not exist, so a parent table must be created before any
     * child that references it. departments and reporters carry no foreign
     * keys and come first; users depends on departments; disaster_reports
     * depends on reporters; assessments, response_tasks and alerts depend on
     * disaster_reports; status_updates depends on response_tasks.
     *
     * @return the CREATE TABLE statements, in the order they must be run
     */
    @Override
    public List<String> tableDefinitions() {
        return Collections.unmodifiableList(Arrays.asList(
                withOptions(SCHEMA_METADATA),
                withOptions(DEPARTMENTS),
                withOptions(REPORTERS),
                withOptions(USERS),
                withOptions(DISASTER_REPORTS),
                withOptions(ASSESSMENTS),
                withOptions(RESPONSE_TASKS),
                withOptions(STATUS_UPDATES),
                withOptions(ALERTS),
                withOptions(AUDIT_LOG),
                withOptions(REQUEST_LOG)));
    }

    /**
     * Appends the engine, character set and collation to a statement.
     *
     * @param definition the CREATE TABLE statement
     * @return the statement with the shared table options applied
     */
    private static String withOptions(String definition) {
        return definition + TABLE_OPTIONS;
    }
}
