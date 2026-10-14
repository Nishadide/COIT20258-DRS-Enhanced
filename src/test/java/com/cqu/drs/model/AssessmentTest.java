package com.cqu.drs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T27 to T29: deriving the response priority from the severity recorded,
 * and the boundary on the length of the assessment notes.
 */
@DisplayName("Assessment")
class AssessmentTest {

    private Assessment assessmentOf(Severity severity, String notes) {
        return new Assessment("AS-1", severity, notes, "coordinator");
    }

    @Test
    @DisplayName("T27: a severe assessment derives priority P1")
    void derivePriorityDelegatesToSeverity() {
        assertEquals(Priority.P1, assessmentOf(Severity.SEVERE, null).derivePriority());
    }

    @Test
    @DisplayName("T27: every severity derives the priority its level warrants")
    void derivePriorityCoversEverySeverity() {
        assertEquals(Priority.P1, assessmentOf(Severity.SEVERE, null).derivePriority());
        assertEquals(Priority.P2, assessmentOf(Severity.HIGH, null).derivePriority());
        assertEquals(Priority.P3, assessmentOf(Severity.MEDIUM, null).derivePriority());
        assertEquals(Priority.P4, assessmentOf(Severity.LOW, null).derivePriority());
    }

    @Test
    @DisplayName("T28: notes of 500 characters are accepted and kept in full")
    void notesAcceptsUpperBound() {
        String atLimit = "x".repeat(DrsConstants.ASSESSMENT_NOTES_MAX_LENGTH);
        Assessment assessment = assessmentOf(Severity.HIGH, atLimit);
        assertEquals(atLimit, assessment.getNotes());
    }

    @Test
    @DisplayName("T29: notes of 501 characters are refused")
    void notesRejectsAboveUpperBound() {
        String overLimit = "x".repeat(DrsConstants.ASSESSMENT_NOTES_MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> assessmentOf(Severity.HIGH, overLimit));
    }

    @Test
    @DisplayName("Notes are optional and a blank note is treated as absent")
    void notesMayBeAbsent() {
        assertNull(assessmentOf(Severity.LOW, null).getNotes());
        assertNull(assessmentOf(Severity.LOW, "   ").getNotes());
    }

    @Test
    @DisplayName("An assessment must record a severity")
    void severityIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> assessmentOf(null, "Confirmed on site"));
    }

    @Test
    @DisplayName("An assessment is stamped with the time it was made and who made it")
    void assessmentRecordsWhenAndWho() {
        Assessment assessment = assessmentOf(Severity.MEDIUM, "Contained");
        assertNotNull(assessment.getAssessedAt());
        assertEquals("coordinator", assessment.getAssessedBy());
    }

    @Test
    @DisplayName("toString names the severity and the priority it produces")
    void toStringShowsSeverityAndPriority() {
        String text = assessmentOf(Severity.SEVERE, null).toString();
        assertTrue(text.contains("SEVERE"));
        assertTrue(text.contains("P1"));
    }

    @Test
    @DisplayName("Notes are trimmed before they are stored")
    void notesAreTrimmed() {
        assertEquals("Confirmed on site",
                assessmentOf(Severity.HIGH, "  Confirmed on site  ").getNotes());
    }

    @Test
    @DisplayName("A note of exactly one character is accepted")
    void notesAcceptsSingleCharacter() {
        assertDoesNotThrow(() -> assessmentOf(Severity.LOW, "x"));
    }
}
