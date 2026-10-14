package com.cqu.drs.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the third concrete user role. The coordinator is the only subclass of
 * User not otherwise exercised by the model tests, and it carries an
 * identifier of its own in addition to what it inherits.
 */
@DisplayName("EmergencyCoordinator")
class EmergencyCoordinatorTest {

    private static final String PASSWORD = "Str0ngPass";

    private EmergencyCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new EmergencyCoordinator("U-10", "c.ordinator", PASSWORD,
                "C. Ordinator", "EC-001");
    }

    @Test
    @DisplayName("A coordinator is created with the coordinator role")
    void coordinatorHoldsCoordinatorRole() {
        assertEquals(UserRole.COORDINATOR, coordinator.getRole());
    }

    @Test
    @DisplayName("The operational identifier is kept alongside the inherited fields")
    void coordinatorIdIsStored() {
        assertEquals("EC-001", coordinator.getCoordinatorId());
        assertEquals("c.ordinator", coordinator.getUsername());
        assertEquals("C. Ordinator", coordinator.getFullName());
    }

    @Test
    @DisplayName("The identifier can be changed after creation")
    void coordinatorIdCanBeChanged() {
        coordinator.setCoordinatorId("EC-002");
        assertEquals("EC-002", coordinator.getCoordinatorId());
    }

    @Test
    @DisplayName("Authentication is inherited and works on the subclass")
    void inheritedAuthenticationWorks() {
        assertTrue(coordinator.authenticate(PASSWORD));
        assertFalse(coordinator.authenticate("WrongPassword"));
    }

    @Test
    @DisplayName("A password change is inherited and works on the subclass")
    void inheritedPasswordChangeWorks() {
        coordinator.changePassword("Replacement1");
        assertTrue(coordinator.authenticate("Replacement1"));
        assertFalse(coordinator.authenticate(PASSWORD));
        assertFalse(coordinator.isMustChangePassword());
    }

    @Test
    @DisplayName("A new coordinator must replace the password it was given")
    void newCoordinatorMustChangePassword() {
        assertTrue(coordinator.isMustChangePassword());
    }

    @Test
    @DisplayName("A coordinator starts active and can be deactivated")
    void coordinatorCanBeDeactivated() {
        assertTrue(coordinator.isActive());
        coordinator.setActive(false);
        assertFalse(coordinator.isActive());
    }

    @Test
    @DisplayName("toString names the person, the role and the identifier")
    void toStringDescribesTheCoordinator() {
        String text = coordinator.toString();
        assertTrue(text.contains("C. Ordinator"));
        assertTrue(text.contains("EC-001"));
        assertTrue(text.contains("Emergency Coordinator"));
        assertFalse(text.contains(PASSWORD), "The password must never be shown.");
    }

    @Test
    @DisplayName("The password is stored only as a digest")
    void passwordIsHashed() {
        assertNotEquals(PASSWORD, coordinator.getPasswordHash());
    }

    @Test
    @DisplayName("The inherited username rules apply to a coordinator")
    void inheritedUsernameRulesApply() {
        assertThrows(IllegalArgumentException.class, () ->
                new EmergencyCoordinator("U-11", "ab", PASSWORD, "A. B", "EC-003"));
    }

    @Test
    @DisplayName("Two coordinators with the same identity are equal")
    void equalityIsByUserId() {
        EmergencyCoordinator same = new EmergencyCoordinator("U-10", "other.name",
                PASSWORD, "Another Person", "EC-999");
        assertEquals(coordinator, same);
        assertEquals(coordinator.hashCode(), same.hashCode());
    }
}
