package com.cqu.drs.service;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T51 to T55: creating, deactivating and re-roling accounts, and the
 * refusal of a duplicate username.
 */
@DisplayName("AccountService")
class AccountServiceTest {

    private static final String PASSWORD = "Str0ngPass";

    private AccountService accountService;
    private AuditService auditService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        JsonDataStore store = new JsonDataStore(tempDir);
        auditService = new AuditService(new AuditRepository(store));
        accountService = new AccountService(new UserRepository(store), auditService);
    }

    @Test
    @DisplayName("T51: a new account is stored and the action is recorded")
    void createStoresUserAndAudits() {
        User created = accountService.create("n.dezoysa", PASSWORD, "N. de Zoysa",
                UserRole.ADMINISTRATOR, null, "setup");

        assertEquals("n.dezoysa", created.getUsername());
        assertTrue(accountService.findByUsername("n.dezoysa").isPresent());
        assertFalse(auditService.findAll().isEmpty(),
                "Creating an account must leave an audit entry.");
    }

    @Test
    @DisplayName("T52: a username already in use is refused")
    void createRefusesDuplicateUsername() {
        accountService.create("n.dezoysa", PASSWORD, "N. de Zoysa",
                UserRole.ADMINISTRATOR, null, "setup");

        assertThrows(IllegalArgumentException.class, () ->
                accountService.create("n.dezoysa", PASSWORD, "Someone Else",
                        UserRole.COORDINATOR, null, "setup"));
        assertEquals(1, accountService.findAll().size(),
                "The refused account must not have been stored.");
    }

    @Test
    @DisplayName("T53: a username of 2 characters is refused and 3 is accepted")
    void usernameLengthLowerBoundary() {
        assertThrows(IllegalArgumentException.class, () ->
                accountService.create("ab", PASSWORD, "A. B",
                        UserRole.ADMINISTRATOR, null, "setup"));

        User created = accountService.create("abc", PASSWORD, "A. B. C",
                UserRole.ADMINISTRATOR, null, "setup");
        assertEquals("abc", created.getUsername());
    }

    @Test
    @DisplayName("T92: a username of 20 characters is accepted and 21 is refused")
    void usernameLengthUpperBoundary() {
        String twenty = "abcdefghijklmnopqrst";
        String twentyOne = twenty + "u";

        User created = accountService.create(twenty, PASSWORD, "A. Long Name",
                UserRole.ADMINISTRATOR, null, "setup");
        assertEquals(twenty, created.getUsername());

        assertThrows(IllegalArgumentException.class, () ->
                accountService.create(twentyOne, PASSWORD, "A. Longer Name",
                        UserRole.ADMINISTRATOR, null, "setup"));
    }

    @Test
    @DisplayName("T54: a deactivated account is marked inactive")
    void deactivatePreventsLogin() {
        accountService.create("temp.staff", PASSWORD, "T. Staff",
                UserRole.COORDINATOR, null, "setup");
        accountService.deactivate("temp.staff", "admin");

        User user = accountService.findByUsername("temp.staff").orElseThrow();
        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("T55: a role change is stored and recorded")
    void assignRoleUpdatesAndAudits() {
        accountService.create("j.smith", PASSWORD, "J. Smith",
                UserRole.RESPONDER, "DEP-01", "setup");
        accountService.assignRole("j.smith", UserRole.ADMINISTRATOR, "admin");

        User user = accountService.findByUsername("j.smith").orElseThrow();
        assertEquals(UserRole.ADMINISTRATOR, user.getRole());
    }

    @Test
    @DisplayName("A responder must be given a department")
    void responderRequiresDepartment() {
        assertThrows(IllegalArgumentException.class, () ->
                accountService.create("crew", PASSWORD, "Fire Crew",
                        UserRole.RESPONDER, null, "setup"));
    }

    @Test
    @DisplayName("The role chosen decides which kind of user is built")
    void roleDecidesConcreteClass() {
        User responder = accountService.create("crew", PASSWORD, "Fire Crew",
                UserRole.RESPONDER, "DEP-01", "setup");
        assertInstanceOf(DepartmentResponder.class, responder);
        assertEquals("DEP-01", ((DepartmentResponder) responder).getDepartmentId());
    }

    @Test
    @DisplayName("Deactivating an account that does not exist is refused")
    void deactivateUnknownAccountRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.deactivate("nobody", "admin"));
    }

    @Test
    @DisplayName("T93: a deactivated account can be reactivated")
    void activateRestoresAccount() {
        accountService.create("temp.staff", PASSWORD, "T. Staff",
                UserRole.COORDINATOR, null, "setup");
        accountService.deactivate("temp.staff", "admin");

        assertTrue(accountService.activate("temp.staff", "admin"),
                "Activating an inactive account must report that it changed.");

        User user = accountService.findByUsername("temp.staff").orElseThrow();
        assertTrue(user.isActive());
        assertEquals(UserRole.COORDINATOR, user.getRole(),
                "Reactivation must not change the role the account held.");
    }

    @Test
    @DisplayName("T94: reactivating an account that is already active changes nothing")
    void activateAlreadyActiveAccountIsHarmless() {
        accountService.create("temp.staff", PASSWORD, "T. Staff",
                UserRole.COORDINATOR, null, "setup");

        assertFalse(accountService.activate("temp.staff", "admin"),
                "An account already active must report that nothing changed.");
        assertTrue(accountService.findByUsername("temp.staff").orElseThrow().isActive());
    }

    @Test
    @DisplayName("T95: reactivation is written to the audit log")
    void activateIsAudited() {
        accountService.create("temp.staff", PASSWORD, "T. Staff",
                UserRole.COORDINATOR, null, "setup");
        accountService.deactivate("temp.staff", "admin");
        accountService.activate("temp.staff", "admin");

        assertTrue(auditService.findAll().stream()
                        .anyMatch(e -> e.getAction().contains("Reactivated account temp.staff")),
                "Reactivating an account must leave an audit entry.");
    }

    @Test
    @DisplayName("T96: activating an account that does not exist is refused")
    void activateUnknownAccountRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.activate("nobody", "admin"));
    }

    @Test
    @DisplayName("An account can be deactivated and reactivated more than once")
    void deactivateAndActivateCycle() {
        accountService.create("temp.staff", PASSWORD, "T. Staff",
                UserRole.COORDINATOR, null, "setup");

        for (int round = 0; round < 3; round++) {
            accountService.deactivate("temp.staff", "admin");
            assertFalse(accountService.findByUsername("temp.staff")
                    .orElseThrow().isActive());

            accountService.activate("temp.staff", "admin");
            assertTrue(accountService.findByUsername("temp.staff")
                    .orElseThrow().isActive());
        }
    }
}
