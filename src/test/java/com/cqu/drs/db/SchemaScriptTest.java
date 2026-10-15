package com.cqu.drs.db;

import com.cqu.drs.model.Department;
import com.cqu.drs.model.SeedData;
import com.cqu.drs.model.User;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T312 to T316: the submitted SQL script says what the code does.
 *
 * <h2>Why this test exists</h2>
 * The specification asks for the schema to be created programmatically and for
 * an SQL script to be submitted. Those are two statements of the same fact,
 * and two statements of the same fact drift apart. The usual way it happens is
 * ordinary: somebody adds a column in {@code CoreSchema}, the application
 * works, the script is forgotten, and the marker who runs the script gets a
 * database the application cannot use.
 *
 * <p>The script is generated from the same definitions the application
 * executes, which removes the chance of writing the two differently. This test
 * closes the remaining gap, which is forgetting to regenerate. If the
 * committed file and the generator disagree by one character, this fails and
 * says what to run.
 *
 * <p>It is also the test that will fail first when the other two members of
 * the team add their own schema modules, which is the point: their tables
 * belong in the submitted script too, and nobody has to remember that.
 */
@DisplayName("The submitted SQL script")
class SchemaScriptTest {

    private static final Path SCRIPT = Path.of("docs", "drs_enhanced_schema.sql");

    private static final String REGENERATE =
            "Regenerate it with:" + System.lineSeparator()
            + "  mvn -q compile exec:java "
            + "-Dexec.mainClass=com.cqu.drs.db.SchemaTool "
            + "-Dexec.args=--print-sql > docs/drs_enhanced_schema.sql";

    /**
     * Skips when the test is not being run from the project directory.
     *
     * The path is relative, because the script's place in the repository is
     * the thing being checked. Maven runs tests from the project directory, so
     * this holds for the build; it would not hold if somebody ran the tests
     * from somewhere else, and skipping is better than failing for a reason
     * that has nothing to do with the schema.
     */
    private static void requireProjectDirectory() {
        Assumptions.assumeTrue(Files.isDirectory(Path.of("docs")),
                "These tests read docs/drs_enhanced_schema.sql and so must be run "
                        + "from the project directory.");
    }

    @Test
    @DisplayName("T312: the committed script is exactly what the code generates")
    void committedScriptMatchesTheCode() throws IOException {
        requireProjectDirectory();

        assertTrue(Files.exists(SCRIPT),
                "The submitted script is missing. " + REGENERATE);

        String committed = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        String generated = SchemaTool.sqlScript();

        assertEquals(generated, committed,
                "The committed script no longer matches the schema the "
                        + "application creates. " + REGENERATE);
    }

    @Test
    @DisplayName("T313: every table the application creates appears in the script")
    void everyTableAppearsInTheScript() {
        String script = SchemaTool.sqlScript();

        for (SchemaModule module : SchemaModules.all()) {
            for (String definition : module.tableDefinitions()) {
                assertTrue(script.contains(definition),
                        "A table definition from the " + module.name()
                                + " module is not in the script.");
            }
        }
    }

    @Test
    @DisplayName("T314: the script creates the database and selects it before creating tables")
    void scriptIsRunnableOnItsOwn() {
        String script = SchemaTool.sqlScript();

        int createDatabase = script.indexOf("CREATE DATABASE IF NOT EXISTS");
        int use = script.indexOf("USE `");
        int firstTable = script.indexOf("CREATE TABLE");

        assertTrue(createDatabase >= 0, "The script does not create the database.");
        assertTrue(use > createDatabase,
                "The script must select the database after creating it.");
        assertTrue(firstTable > use,
                "A CREATE TABLE before the USE would land in whichever database "
                        + "the person running the script happened to be in.");
    }

    @Test
    @DisplayName("T315: every statement in the script is terminated")
    void everyStatementIsTerminated() {
        String script = SchemaTool.sqlScript();

        // A missing semicolon is the one fault that makes a script fail for
        // somebody else and work when pasted in by whoever wrote it.
        long creates = script.lines().filter(line -> line.startsWith("CREATE")).count();
        long terminators = script.lines().filter(line -> line.trim().endsWith(";")).count();

        assertTrue(creates > 0, "The script creates nothing.");
        assertTrue(terminators >= creates,
                "There are " + creates + " statements and only " + terminators
                        + " terminated lines.");
    }

    @Test
    @DisplayName("T316: the script carries no password and no host")
    void scriptCarriesNoCredentials() {
        String script = SchemaTool.sqlScript().toLowerCase();

        // The script is submitted and will be read by people who are not on
        // the team. It describes tables; it has no business carrying the
        // connection settings, and a generator that interpolated them would
        // put a password in a file somebody is about to email.
        assertFalse(script.contains("password ="), script);
        assertFalse(script.contains("identified by"),
                "The script must not create or alter any MySQL account.");
        assertFalse(script.contains("grant "),
                "Granting privileges is the database administrator's decision, "
                        + "not this script's.");
    }

    @Test
    @DisplayName("T321: every department the application seeds is in the script")
    void scriptSeedsEveryDepartment() {
        String script = SchemaTool.sqlScript();

        assertTrue(script.contains("INSERT INTO departments"),
                "The task plan asks for a script that creates and populates. A "
                        + "database with eleven empty tables cannot be signed into "
                        + "and cannot dispatch anything.");

        for (Department department : SeedData.departments()) {
            assertTrue(script.contains("'" + department.getDepartmentId() + "'"),
                    department.getDepartmentId() + " is missing from the script.");
            assertTrue(script.contains("'" + department.getName() + "'"),
                    department.getName() + " is missing from the script.");
        }
    }

    @Test
    @DisplayName("T322: an account the script inserts can sign in with its documented password")
    void scriptAccountsCanSignIn() {
        String script = SchemaTool.sqlScript();

        for (SeedData.StartingAccount account : SeedData.startingAccounts()) {
            User user = account.toUser(account.seedUserId());

            // The hash in the script is the one the model computes, so a row
            // written by the script and a row written by the application are
            // the same row. If these ever differ, the documented password
            // works on a database the application built and not on one the
            // script built, which is the kind of fault that is blamed on
            // everything except the script.
            assertTrue(user.authenticate(account.plainPassword()),
                    "The documented password for " + account.username()
                            + " does not match the hash the model computes.");
            assertTrue(script.contains("'" + user.getPasswordHash() + "'"),
                    "The hash for " + account.username() + " is not in the script.");
            assertTrue(script.contains("'" + account.username() + "'"),
                    account.username() + " is not in the script.");
        }
    }

    @Test
    @DisplayName("T323: the reference data can be inserted twice without failing")
    void referenceDataIsUpserted() {
        String script = SchemaTool.sqlScript();

        // Both the script and the application can seed, and either may run
        // first. A plain INSERT would make whichever ran second fail on a
        // duplicate key.
        int inserts = script.split("INSERT INTO", -1).length - 1;
        int upserts = script.split("ON DUPLICATE KEY UPDATE", -1).length - 1;

        assertEquals(inserts, upserts,
                "Every insert in the script must be an upsert: " + inserts
                        + " inserts and " + upserts + " upserts.");
    }
}
