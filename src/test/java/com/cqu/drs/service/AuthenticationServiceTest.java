package com.cqu.drs.service;

import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.UserRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T46 to T50: signing in and out. Each test writes to its own temporary
 * folder, so no test can be affected by data another one left behind.
 */
@DisplayName("AuthenticationService")
class AuthenticationServiceTest {

    private static final String PASSWORD = "Str0ngPass";

    private AuthenticationService authService;
    private AccountService accountService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        JsonDataStore store = new JsonDataStore(tempDir);
        UserRepository userRepository = new JsonUserRepository(store);
        AuditService auditService = new AuditService(new JsonAuditRepository(store));

        accountService = new AccountService(userRepository, auditService);
        authService = new AuthenticationService(userRepository, auditService);

        accountService.create("coordinator", PASSWORD, "C. Ordinator",
                UserRole.COORDINATOR, null, "setup");
    }

    @Test
    @DisplayName("T46: correct credentials on an active account sign the user in")
    void loginReturnsUserWhenValid() {
        Optional<User> result = authService.login("coordinator", PASSWORD);
        assertTrue(result.isPresent());
        assertEquals("coordinator", result.get().getUsername());
        assertTrue(authService.getCurrentUser().isPresent());
    }

    @Test
    @DisplayName("T47: the wrong password is refused")
    void loginEmptyWhenPasswordWrong() {
        assertTrue(authService.login("coordinator", "WrongPassword").isEmpty());
        assertTrue(authService.getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("T48: an unknown username returns an empty Optional rather than null")
    void loginEmptyWhenUserUnknown() {
        Optional<User> result = authService.login("nobody", PASSWORD);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("T49: a deactivated account cannot sign in even with the right password")
    void loginRefusedWhenDeactivated() {
        accountService.deactivate("coordinator", "admin");
        assertTrue(authService.login("coordinator", PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("T50: signing out ends the session")
    void logoutEndsSession() {
        authService.login("coordinator", PASSWORD);
        assertTrue(authService.getCurrentUser().isPresent());

        authService.logout();
        assertTrue(authService.getCurrentUser().isEmpty());
    }

    @Test
    @DisplayName("A username is matched without regard to case")
    void loginIgnoresUsernameCase() {
        assertTrue(authService.login("COORDINATOR", PASSWORD).isPresent());
    }

    @Test
    @DisplayName("A password cannot be changed when nobody is signed in")
    void changePasswordRefusedWithoutSession() {
        assertThrows(IllegalStateException.class,
                () -> authService.changeCurrentPassword("NewPassw0rd"));
    }

    @Test
    @DisplayName("A changed password persists and replaces the old one")
    void changedPasswordPersists() {
        authService.login("coordinator", PASSWORD);
        authService.changeCurrentPassword("NewPassw0rd");
        authService.logout();

        assertTrue(authService.login("coordinator", "NewPassw0rd").isPresent());
        assertTrue(authService.login("coordinator", PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("T97: a reactivated account can sign in again with its old password")
    void reactivatedAccountCanSignInAgain() {
        accountService.deactivate("coordinator", "admin");
        assertTrue(authService.login("coordinator", PASSWORD).isEmpty(),
                "A deactivated account must not be able to sign in.");

        accountService.activate("coordinator", "admin");

        Optional<User> result = authService.login("coordinator", PASSWORD);
        assertTrue(result.isPresent(),
                "A reactivated account must be able to sign in again.");
        assertEquals(UserRole.COORDINATOR, result.get().getRole());
    }
}
