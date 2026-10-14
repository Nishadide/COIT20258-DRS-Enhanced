package com.cqu.drs.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T115 to T121: the shape of the schema definitions.
 *
 * These need no database. They test the properties the schema has to hold for
 * the initialiser to work at all, and they catch the two mistakes that are
 * easy to make and awkward to diagnose: a table placed before the table it
 * references, and a statement that would fail on a second run.
 *
 * One of these tests exists because of a defect found by executing the schema
 * rather than reading it, and it is recorded in PROBLEM-LOG.md.
 */
@DisplayName("Core schema definitions")
class CoreSchemaTest {

    private static final Pattern TABLE_NAME =
            Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)");
    private static final Pattern REFERENCED_TABLE =
            Pattern.compile("REFERENCES\\s+(\\w+)\\s*\\(");

    private final List<String> definitions = new CoreSchema().tableDefinitions();

    @Test
    @DisplayName("T115: the eleven core tables are defined, with the expected names")
    void allCoreTablesAreDefined() {
        Set<String> expected = new LinkedHashSet<>(List.of(
                "schema_metadata", "departments", "reporters", "users",
                "disaster_reports", "assessments", "response_tasks",
                "status_updates", "alerts", "audit_log", "request_log"));

        assertEquals(expected, new LinkedHashSet<>(tableNames()),
                "A table added or renamed without updating this test means the "
                        + "schema and the documented design have drifted apart.");
    }

    @Test
    @DisplayName("T116: every statement is written so that a second run changes nothing")
    void everyStatementIsIdempotent() {
        for (String definition : definitions) {
            assertTrue(definition.contains("CREATE TABLE IF NOT EXISTS"),
                    "Without IF NOT EXISTS this statement fails the second time "
                            + "the server starts: " + firstLine(definition));
        }
    }

    @Test
    @DisplayName("T117: every table is InnoDB with the utf8mb4 character set")
    void everyTableUsesInnoDbAndUtf8mb4() {
        for (String definition : definitions) {
            // InnoDB is the only engine supporting both foreign keys and
            // transactions, and both are relied on.
            assertTrue(definition.contains("ENGINE=InnoDB"),
                    firstLine(definition) + " must be InnoDB.");
            assertTrue(definition.contains("CHARSET=utf8mb4"),
                    firstLine(definition) + " must use utf8mb4.");
        }
    }

    @Test
    @DisplayName("T118: a referenced table is always created before the table referencing it")
    void foreignKeysPointBackwards() {
        Set<String> createdSoFar = new LinkedHashSet<>();

        for (String definition : definitions) {
            String table = tableNameOf(definition);

            Matcher references = REFERENCED_TABLE.matcher(definition);
            while (references.find()) {
                String referenced = references.group(1);
                assertTrue(createdSoFar.contains(referenced) || referenced.equals(table),
                        table + " has a foreign key to " + referenced
                                + ", which has not been created yet. MySQL rejects a "
                                + "foreign key to a table that does not exist, so "
                                + referenced + " must be listed earlier.");
            }

            createdSoFar.add(table);
        }
    }

    @Test
    @DisplayName("T119: no foreign key uses ON UPDATE CASCADE")
    void noForeignKeyCascadesOnUpdate() {
        // This is a regression test for a defect found by running the schema
        // against a live server. A check constraint referring to a column that
        // is also a foreign key is rejected outright when the foreign key can
        // alter that column, which ON UPDATE CASCADE can. The users table
        // needs exactly that combination, because
        // chk_users_responder_has_department refers to department_id.
        //
        // No primary key in this schema is ever updated, so the cascade was
        // never going to fire and nothing is lost by declaring RESTRICT. This
        // test stops it being added back by someone who has not read why.
        for (String definition : definitions) {
            assertFalse(definition.contains("ON UPDATE CASCADE"),
                    firstLine(definition) + " declares ON UPDATE CASCADE. Use "
                            + "ON UPDATE RESTRICT: see the class comment on "
                            + "CoreSchema for why.");
        }
    }

    @Test
    @DisplayName("T120: the rule that a responder must have a department is enforced by the database")
    void responderDepartmentRuleIsInTheSchema() {
        // AccountService enforces this in Java and AccountServiceTest proves
        // it. The same rule is enforced again in the schema, so a row written
        // by any route other than the application still cannot break it.
        String users = definitionOf("users");
        assertTrue(users.contains("chk_users_responder_has_department"),
                "The schema must enforce the rule, not only the service layer.");
        assertTrue(users.contains("role <> 'RESPONDER' OR department_id IS NOT NULL"));
    }

    @Test
    @DisplayName("T121: a report may hold at most one assessment, enforced by a unique key")
    void oneAssessmentPerReportIsEnforced() {
        // The class diagram gives a report zero or one assessment. Without the
        // unique key the schema would permit many, and the diagram would
        // describe something the database does not do.
        assertTrue(definitionOf("assessments")
                        .contains("UNIQUE KEY uq_assessments_report (reference_number)"),
                "The zero-or-one multiplicity in the class diagram is enforced "
                        + "by this unique key.");
    }

    @Test
    @DisplayName("A department is dispatched to a disaster at most once")
    void oneTaskPerDepartmentPerDisaster() {
        assertTrue(definitionOf("response_tasks")
                .contains("UNIQUE KEY uq_tasks_report_department "
                        + "(reference_number, department_id)"));
    }

    @Test
    @DisplayName("Timestamps keep microsecond precision and carry no time zone")
    void timestampsAreDateTimeWithMicroseconds() {
        // TIMESTAMP would convert to and from UTC using the session time zone,
        // so a LocalDateTime written on one machine would read back changed on
        // another.
        for (String definition : definitions) {
            assertFalse(definition.matches("(?s).*\\bTIMESTAMP\\b.*"),
                    firstLine(definition) + " uses TIMESTAMP. Use DATETIME(6).");
        }
        assertTrue(definitionOf("audit_log").contains("timestamp      DATETIME(6)"));
    }

    @Test
    @DisplayName("The audit log orders its entries with a database-assigned sequence")
    void auditLogSequenceIsAssignedByTheDatabase() {
        // Several server threads append at once. Letting the database assign
        // the number means two threads cannot be given the same one, so the
        // hash chain has exactly one order.
        String auditLog = definitionOf("audit_log");
        assertTrue(auditLog.contains("sequence_no    BIGINT       NOT NULL AUTO_INCREMENT"));
        assertTrue(auditLog.contains("previous_hash  CHAR(64)     NOT NULL"));
        assertTrue(auditLog.contains("entry_hash     CHAR(64)     NOT NULL"));
    }

    @Test
    @DisplayName("Replay of a request identifier is refused by the database, not only in memory")
    void requestIdentifierIsUnique() {
        // In memory replay detection is lost when the server restarts.
        assertTrue(definitionOf("request_log")
                .contains("UNIQUE KEY uq_request_log_request (request_id)"));
    }

    @Test
    @DisplayName("The core module is registered first, because everything references it")
    void coreModuleIsRegisteredFirst() {
        List<SchemaModule> modules = SchemaModules.all();
        assertFalse(modules.isEmpty(), "At least the core module must be registered.");
        assertEquals("core", modules.get(0).name());
    }

    @Test
    @DisplayName("An encrypted column is sized for the ciphertext, not the plaintext")
    void encryptedColumnsAreSizedForCiphertext() {
        // contact_number holds at most 15 digits in plaintext, but is stored
        // as a base64 initialisation vector and ciphertext. A column sized for
        // the plaintext would truncate it and the value would never decrypt.
        assertTrue(definitionOf("reporters").contains("contact_number  VARCHAR(255)"));
    }

    private List<String> tableNames() {
        List<String> names = new ArrayList<>();
        for (String definition : definitions) {
            names.add(tableNameOf(definition));
        }
        return names;
    }

    private String tableNameOf(String definition) {
        Matcher matcher = TABLE_NAME.matcher(definition);
        assertTrue(matcher.find(),
                "Every statement must be a CREATE TABLE IF NOT EXISTS: "
                        + firstLine(definition));
        return matcher.group(1);
    }

    private String definitionOf(String tableName) {
        for (String definition : definitions) {
            if (tableNameOf(definition).equals(tableName)) {
                return definition;
            }
        }
        throw new AssertionError("No definition found for table " + tableName);
    }

    private static String firstLine(String definition) {
        int newline = definition.indexOf('\n');
        return newline < 0 ? definition : definition.substring(0, newline).trim();
    }
}
