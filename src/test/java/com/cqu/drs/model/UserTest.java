package com.cqu.drs.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T41 to T45: checking a password, replacing one, and the minimum length
 * boundary. A fresh user is built for each test so that no test depends on
 * another.
 */
@DisplayName("User authentication")
class UserTest {

    private static final String VALID_PASSWORD = "Str0ngPass";
    private User user;

    @BeforeEach
    void setUp() {
        user = new SystemAdministrator("U-01", "n.dezoysa", VALID_PASSWORD, "N. de Zoysa");
    }

    @Test
    @DisplayName("T41: the correct password is accepted")
    void authenticateAcceptsCorrectPassword() {
        assertTrue(user.authenticate(VALID_PASSWORD));
    }

    @Test
    @DisplayName("T42: an incorrect password is refused")
    void authenticateRejectsWrongPassword() {
        assertFalse(user.authenticate("WrongPassword"));
    }

    @Test
    @DisplayName("T43: an empty password is refused without an error")
    void authenticateHandlesEmptyPassword() {
        assertDoesNotThrow(() -> user.authenticate(""));
        assertFalse(user.authenticate(""));
        assertFalse(user.authenticate(null));
    }

    @Test
    @DisplayName("T44: a replacement password of 8 characters is accepted")
    void changePasswordAcceptsMinimumLength() {
        user.changePassword("12345678");
        assertTrue(user.authenticate("12345678"));
        assertFalse(user.authenticate(VALID_PASSWORD),
                "The old password should stop working once it is replaced.");
    }

    @Test
    @DisplayName("T45: a replacement password of 7 characters is refused")
    void changePasswordRejectsShortPassword() {
        assertThrows(IllegalArgumentException.class, () -> user.changePassword("1234567"));
        assertTrue(user.authenticate(VALID_PASSWORD),
                "The old password should still work after a refused change.");
    }

    @Test
    @DisplayName("The password is not stored in readable form")
    void passwordIsNotStoredInPlainText() {
        assertNotEquals(VALID_PASSWORD, user.getPasswordHash());
        assertFalse(user.toString().contains(VALID_PASSWORD));
    }

    @Test
    @DisplayName("A new account must replace its initial password")
    void newAccountMustChangePassword() {
        assertTrue(user.isMustChangePassword());
        user.changePassword("Replacement1");
        assertFalse(user.isMustChangePassword());
    }

    @Test
    @DisplayName("T53: a username of 2 characters is refused and 3 is accepted")
    void usernameLengthLowerBoundary() {
        assertThrows(IllegalArgumentException.class,
                () -> new SystemAdministrator("U-02", "ab", VALID_PASSWORD, "A. B"));
        assertDoesNotThrow(
                () -> new SystemAdministrator("U-03", "abc", VALID_PASSWORD, "A. B. C"));
    }

    @Test
    @DisplayName("A responder may only update work for its own department")
    void responderCanOnlyUpdateOwnDepartment() {
        DepartmentResponder responder = new DepartmentResponder(
                "U-04", "fire.crew", VALID_PASSWORD, "Fire Crew", "DEP-01");
        ResponseTask own = new ResponseTask("RT-1",
                new Department("DEP-01", "Fire and Emergency", "a@b.c"));
        ResponseTask other = new ResponseTask("RT-2",
                new Department("DEP-02", "Hospital", "a@b.c"));
        assertTrue(responder.canUpdate(own));
        assertFalse(responder.canUpdate(other));
        assertFalse(responder.canUpdate(null));
    }
}
