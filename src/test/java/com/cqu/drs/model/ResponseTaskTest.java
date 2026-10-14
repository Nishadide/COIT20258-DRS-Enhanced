package com.cqu.drs.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T30 to T36: the status history a task keeps, and the refusal to accept
 * further updates once the work is finished.
 */
@DisplayName("ResponseTask and its history")
class ResponseTaskTest {

    private ResponseTask task;

    @BeforeEach
    void setUp() {
        task = new ResponseTask("RT-1",
                new Department("DEP-01", "Fire and Emergency", "dispatch@fire.gov.au"));
    }

    private StatusUpdate update(String id, ResponseStatus status) {
        return new StatusUpdate(id, status, null, "crew");
    }

    @Test
    @DisplayName("T30: a newly dispatched task is not complete")
    void isCompleteFalseWhenDispatched() {
        assertEquals(ResponseStatus.DISPATCHED, task.getStatus());
        assertFalse(task.isComplete());
    }

    @Test
    @DisplayName("T35: a new task has an empty history rather than none")
    void newTaskHasEmptyHistory() {
        assertNotNull(task.getHistory());
        assertTrue(task.getHistory().isEmpty());
    }

    @Test
    @DisplayName("T31: an update is recorded and moves the task on")
    void addUpdateAppendsAndSetsStatus() {
        task.addUpdate(update("SU-1", ResponseStatus.IN_PROGRESS));
        assertEquals(1, task.getHistory().size());
        assertEquals(ResponseStatus.IN_PROGRESS, task.getStatus());
    }

    @Test
    @DisplayName("T32: several updates are kept in the order they were made")
    void addUpdateKeepsChronologicalOrder() {
        task.addUpdate(update("SU-1", ResponseStatus.IN_PROGRESS));
        task.addUpdate(update("SU-2", ResponseStatus.IN_PROGRESS));
        task.addUpdate(update("SU-3", ResponseStatus.COMPLETE));

        assertEquals(3, task.getHistory().size());
        assertEquals("SU-1", task.getHistory().get(0).getUpdateId());
        assertEquals("SU-3", task.getHistory().get(2).getUpdateId());
    }

    @Test
    @DisplayName("T33: a task is complete once a COMPLETE update is recorded")
    void isCompleteTrueWhenComplete() {
        task.addUpdate(update("SU-1", ResponseStatus.COMPLETE));
        assertTrue(task.isComplete());
    }

    @Test
    @DisplayName("T34: a finished task accepts no further updates")
    void addUpdateRefusedWhenComplete() {
        task.addUpdate(update("SU-1", ResponseStatus.COMPLETE));
        assertThrows(IllegalStateException.class,
                () -> task.addUpdate(update("SU-2", ResponseStatus.IN_PROGRESS)));
        assertEquals(1, task.getHistory().size(),
                "A refused update must leave the history unchanged.");
    }

    @Test
    @DisplayName("A null update is refused")
    void addUpdateRefusesNull() {
        assertThrows(IllegalArgumentException.class, () -> task.addUpdate(null));
    }

    @Test
    @DisplayName("T36: a note of 200 characters is accepted and 201 is refused")
    void noteLengthBoundary() {
        String atLimit = "x".repeat(DrsConstants.STATUS_NOTE_MAX_LENGTH);
        String overLimit = "x".repeat(DrsConstants.STATUS_NOTE_MAX_LENGTH + 1);

        assertDoesNotThrow(() ->
                new StatusUpdate("SU-9", ResponseStatus.IN_PROGRESS, atLimit, "crew"));
        assertThrows(IllegalArgumentException.class, () ->
                new StatusUpdate("SU-10", ResponseStatus.IN_PROGRESS, overLimit, "crew"));
    }

    @Test
    @DisplayName("A task remembers which department it was sent to")
    void taskRecordsDepartment() {
        assertEquals("DEP-01", task.getDepartmentId());
        assertEquals("Fire and Emergency", task.getDepartmentName());
        assertNotNull(task.getDispatchedAt());
        assertFalse(task.isNotified(), "A task is not notified until the notice is sent.");
    }

    @Test
    @DisplayName("A task cannot be created without a department")
    void taskRequiresDepartment() {
        assertThrows(IllegalArgumentException.class, () -> new ResponseTask("RT-2", null));
    }

    @Test
    @DisplayName("The history cannot be added to directly")
    void historyIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> task.getHistory().add(update("SU-99", ResponseStatus.COMPLETE)));
    }
}
