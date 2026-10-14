package com.cqu.drs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T37 to T40: broadcasting an alert, the failure path when the channel
 * cannot be reached, and the message length boundary.
 */
@DisplayName("Alert and Department")
class AlertTest {

    private Alert newAlert() {
        return new Alert("AL-1", AlertType.EVACUATION, "Zone 4, Riverside",
                "Leave the area immediately by the northern road.", "coordinator");
    }

    @Test
    @DisplayName("T37: a department reports whether it can take work")
    void isAvailableReflectsField() {
        Department department = new Department("DEP-01", "Fire and Emergency", "a@b.c");
        assertTrue(department.isAvailable());
        department.setAvailable(false);
        assertFalse(department.isAvailable());
        assertTrue(department.toString().contains("unavailable"));
    }

    @Test
    @DisplayName("A new alert has not yet been delivered")
    void newAlertIsUndelivered() {
        assertFalse(newAlert().isDelivered());
    }

    @Test
    @DisplayName("T38: a reachable channel delivers the alert")
    void broadcastSucceedsWhenReachable() {
        Alert alert = newAlert();
        assertTrue(alert.broadcast(true));
        assertTrue(alert.isDelivered());
    }

    @Test
    @DisplayName("T39: an unreachable channel leaves the alert undelivered")
    void broadcastFailsWhenUnreachable() {
        Alert alert = newAlert();
        assertFalse(alert.broadcast(false));
        assertFalse(alert.isDelivered(),
                "A failed broadcast must be visible afterwards, not assumed successful.");
    }

    @Test
    @DisplayName("T40: a message of 300 characters is accepted and 301 is refused")
    void messageLengthBoundary() {
        String atLimit = "x".repeat(DrsConstants.ALERT_MESSAGE_MAX_LENGTH);
        String overLimit = "x".repeat(DrsConstants.ALERT_MESSAGE_MAX_LENGTH + 1);

        assertDoesNotThrow(() ->
                new Alert("AL-2", AlertType.WARNING, "Zone 1", atLimit, "coordinator"));
        assertThrows(IllegalArgumentException.class, () ->
                new Alert("AL-3", AlertType.WARNING, "Zone 1", overLimit, "coordinator"));
    }

    @Test
    @DisplayName("An alert must carry a message and an area")
    void alertRequiresMessageAndArea() {
        assertThrows(IllegalArgumentException.class, () ->
                new Alert("AL-4", AlertType.WARNING, "Zone 1", "   ", "coordinator"));
        assertThrows(IllegalArgumentException.class, () ->
                new Alert("AL-5", AlertType.WARNING, "", "Evacuate now", "coordinator"));
    }

    @Test
    @DisplayName("An audit entry records who did what")
    void auditEntryRecordsActorAndAction() {
        AuditLogEntry entry = new AuditLogEntry("AE-1", "coordinator", "Issued an alert");
        assertEquals("coordinator", entry.getActor());
        assertEquals("Issued an alert", entry.getAction());
        assertTrue(entry.toString().contains("coordinator"));
    }

    @Test
    @DisplayName("An action taken with nobody signed in is recorded as anonymous")
    void auditEntryDefaultsToAnonymous() {
        AuditLogEntry entry = new AuditLogEntry("AE-2", null, "Lodged a report");
        assertEquals("anonymous", entry.getActor());
    }
}
