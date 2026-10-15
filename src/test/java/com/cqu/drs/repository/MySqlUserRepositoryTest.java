package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T227 to T237: user accounts in MySQL.
 *
 * The test that matters most is T229. A user is rebuilt from a row with no
 * plaintext password anywhere, so the one thing that must survive storage is
 * the ability to authenticate. If the hash were lost or replaced by the
 * placeholder the rebuild uses, every account would still look correct in a
 * list and nobody would be able to sign in.
 */
@DisplayName("MySqlUserRepository")
class MySqlUserRepositoryTest {

    private static final String PASSWORD = "Str0ngPass";

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlUserRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();
        DatabaseTestSupport.clearAll(connections);
        seedDepartment();
        repository = new MySqlUserRepository(connections);
    }

    @AfterEach
    void tearDown() {
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    private void seedDepartment() throws SQLException {
        // A responder's department must exist: the foreign key says so.
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT IGNORE INTO departments (department_id, name, contact_email) "
                    + "VALUES ('DEP-01', 'Fire and Emergency', 'dispatch@fire.gov.au')");
        }
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T227: an administrator is stored and read back as a SystemAdministrator")
    void administratorRoundTrips() {
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        User read = repository.findByUsername("admin").orElseThrow();

        assertInstanceOf(SystemAdministrator.class, read);
        assertEquals(UserRole.ADMINISTRATOR, read.getRole());
        assertEquals("A. Dmin", read.getFullName());
        assertEquals("u-1", read.getUserId());
    }

    @Test
    @DisplayName("T228: the role stored decides which class is rebuilt, with its own field")
    void roleDecidesTheClassRebuilt() {
        repository.save(new EmergencyCoordinator("u-2", "coordinator", PASSWORD,
                "C. Ordinator", "EC-0007"));
        repository.save(new DepartmentResponder("u-3", "fire.crew", PASSWORD,
                "Fire Crew", "DEP-01"));

        User coordinator = repository.findByUsername("coordinator").orElseThrow();
        User responder = repository.findByUsername("fire.crew").orElseThrow();

        assertInstanceOf(EmergencyCoordinator.class, coordinator);
        assertEquals("EC-0007", ((EmergencyCoordinator) coordinator).getCoordinatorId());

        assertInstanceOf(DepartmentResponder.class, responder);
        assertEquals("DEP-01", ((DepartmentResponder) responder).getDepartmentId());
    }

    @Test
    @DisplayName("T229: a user read back can still authenticate with their password")
    void storedUserCanStillAuthenticate() {
        // The rebuild constructs the user with a placeholder password, because
        // every constructor in the hierarchy demands one, and then puts the
        // stored hash in. If that last step were ever lost, accounts would
        // look perfectly correct in a list and nobody could sign in.
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        User read = repository.findByUsername("admin").orElseThrow();

        assertTrue(read.authenticate(PASSWORD),
                "The stored hash must survive, or no account can be used.");
        assertFalse(read.authenticate("placeholder-replaced-at-once"),
                "The placeholder the rebuild uses must never become a usable "
                        + "password.");
        assertFalse(read.authenticate("WrongPassword"));
    }

    @Test
    @DisplayName("T230: a username is matched whatever case it is typed in")
    void usernameMatchIgnoresCase() {
        // Assessment 2 matched usernames without regard to case and has a test
        // proving it. The collation on the column was chosen so the behaviour
        // did not change when the storage did.
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        assertTrue(repository.findByUsername("ADMIN").isPresent());
        assertTrue(repository.findByUsername("Admin").isPresent());
        assertTrue(repository.existsByUsername("aDmIn"));
    }

    @Test
    @DisplayName("T231: a username already taken is refused by the database")
    void duplicateUsernameIsRefused() {
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        // A different account claiming the same name. AccountService refuses
        // this too, but the unique key is what makes it impossible rather than
        // merely discouraged.
        assertThrows(IllegalStateException.class,
                () -> repository.save(
                        new SystemAdministrator("u-2", "admin", PASSWORD, "Someone Else")));

        assertEquals(1, repository.findAll().size());
    }

    @Test
    @DisplayName("T232: saving an existing user updates it rather than adding another")
    void savingExistingUserUpdates() {
        User user = new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin");
        repository.save(user);

        user.setFullName("A. Dmin (acting)");
        user.setActive(false);
        repository.save(user);

        assertEquals(1, repository.findAll().size());
        User read = repository.findByUsername("admin").orElseThrow();
        assertEquals("A. Dmin (acting)", read.getFullName());
        assertFalse(read.isActive());
    }

    @Test
    @DisplayName("T233: deactivating and reactivating survives storage")
    void activeFlagRoundTrips() {
        User user = new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin");
        repository.save(user);

        user.setActive(false);
        repository.save(user);
        assertFalse(repository.findByUsername("admin").orElseThrow().isActive());

        user.setActive(true);
        repository.save(user);
        assertTrue(repository.findByUsername("admin").orElseThrow().isActive());
    }

    @Test
    @DisplayName("T234: a changed password is what the stored account authenticates with")
    void changedPasswordIsStored() {
        User user = new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin");
        repository.save(user);

        user.changePassword("NewPassw0rd");
        repository.save(user);

        User read = repository.findByUsername("admin").orElseThrow();
        assertTrue(read.authenticate("NewPassw0rd"));
        assertFalse(read.authenticate(PASSWORD));
    }

    @Test
    @DisplayName("T235: a responder with no department is refused by the database")
    void responderWithoutDepartmentIsRefused() throws SQLException {
        // AccountService refuses this in Java. The check constraint refuses it
        // again, so a row written by any route other than the application
        // still cannot break the rule.
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement()) {

            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO users (user_id, username, password_hash, full_name, role) "
                    + "VALUES ('u-9', 'nodept', 'hash', 'No Dept', 'RESPONDER')"));
        }
    }

    @Test
    @DisplayName("T236: a coordinator and an administrator carry no department")
    void onlyRespondersCarryADepartment() {
        repository.save(new EmergencyCoordinator("u-2", "coordinator", PASSWORD,
                "C. Ordinator", "EC-0007"));
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        assertInstanceOf(EmergencyCoordinator.class,
                repository.findByUsername("coordinator").orElseThrow());
        assertInstanceOf(SystemAdministrator.class,
                repository.findByUsername("admin").orElseThrow());
    }

    @Test
    @DisplayName("T237: the password algorithm is recorded, so a future change can be staged")
    void passwordAlgorithmIsRecorded() throws SQLException {
        // The model stores an unsalted digest today. Recording which algorithm
        // produced it means a move to salted PBKDF2 needs no schema change and
        // can upgrade each account the next time its owner signs in.
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             var results = statement.executeQuery(
                     "SELECT password_algorithm, password_salt FROM users")) {

            assertTrue(results.next());
            assertEquals("SHA-256", results.getString("password_algorithm"));
            assertNull(results.getString("password_salt"),
                    "No salt yet, which is exactly what the algorithm column records.");
        }
    }

    @Test
    @DisplayName("An unknown, empty or absent username finds nothing")
    void unknownUsernameFindsNothing() {
        repository.save(new SystemAdministrator("u-1", "admin", PASSWORD, "A. Dmin"));

        assertTrue(repository.findByUsername("nobody").isEmpty());
        assertTrue(repository.findByUsername("").isEmpty());
        assertTrue(repository.findByUsername(null).isEmpty());
        assertFalse(repository.existsByUsername("nobody"));
        assertFalse(repository.existsByUsername(null));
    }

    @Test
    @DisplayName("Accounts are listed by username")
    void accountsAreListedByUsername() {
        repository.save(new SystemAdministrator("u-1", "zara", PASSWORD, "Z. Ara"));
        repository.save(new SystemAdministrator("u-2", "adam", PASSWORD, "A. Dam"));

        assertEquals("adam", repository.findAll().get(0).getUsername());
        assertEquals("zara", repository.findAll().get(1).getUsername());
    }

    @Test
    @DisplayName("A null user is refused")
    void nullUserRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }
}
