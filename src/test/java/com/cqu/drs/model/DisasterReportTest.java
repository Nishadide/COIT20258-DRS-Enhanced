package com.cqu.drs.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T19 to T26: the states a report passes through, the transitions that
 * are refused, and the attribute round trip.
 */
@DisplayName("DisasterReport lifecycle")
class DisasterReportTest {

    private DisasterReport report;

    @BeforeEach
    void setUp() {
        report = new DisasterReport("DR-0001", DisasterType.FIRE,
                new Location("12 Bridge Street, Melbourne"),
                Severity.HIGH, "Smoke seen from the roof", null);
    }

    private Assessment assessmentOf(Severity severity) {
        return new Assessment("AS-1", severity, "Confirmed on site", "coordinator");
    }

    private ResponseTask taskFor(String id) {
        return new ResponseTask(id, new Department("DEP-01", "Fire and Emergency", "a@b.c"));
    }

    @Test
    @DisplayName("T19: a new report is SUBMITTED")
    void newReportIsSubmitted() {
        assertEquals(ReportStatus.SUBMITTED, report.currentStatus());
    }

    @Test
    @DisplayName("T20: a new report has not been assessed")
    void isAssessedFalseBeforeAssessment() {
        assertFalse(report.isAssessed());
        assertNull(report.getPriority());
    }

    @Test
    @DisplayName("T21: a report reports itself assessed once an assessment is attached")
    void isAssessedTrueAfterAssessment() {
        report.attachAssessment(assessmentOf(Severity.HIGH));
        assertTrue(report.isAssessed());
        assertNotNull(report.getAssessment());
    }

    @Test
    @DisplayName("T22: attaching an assessment moves the report to ASSESSED")
    void statusMovesToAssessed() {
        report.attachAssessment(assessmentOf(Severity.SEVERE));
        assertTrue(report.isAssessed());
        assertEquals(ReportStatus.ASSESSED, report.currentStatus());
        assertEquals(Priority.P1, report.getPriority());
    }

    @Test
    @DisplayName("T23: dispatching the first task moves the report to DISPATCHED")
    void statusMovesToDispatched() {
        report.attachAssessment(assessmentOf(Severity.HIGH));
        report.addTask(taskFor("RT-1"));
        assertEquals(ReportStatus.DISPATCHED, report.currentStatus());
        assertEquals(1, report.getTasks().size());
    }

    @Test
    @DisplayName("T24: a report resolves once every task is complete")
    void statusMovesToResolved() {
        report.attachAssessment(assessmentOf(Severity.MEDIUM));
        ResponseTask task = taskFor("RT-1");
        report.addTask(task);
        task.addUpdate(new StatusUpdate("SU-1", ResponseStatus.COMPLETE, "Done", "crew"));

        report.resolve();
        assertEquals(ReportStatus.RESOLVED, report.currentStatus());
    }

    @Test
    @DisplayName("T25: a report will not resolve while work remains open")
    void resolveRefusedWhileTasksOpen() {
        report.attachAssessment(assessmentOf(Severity.MEDIUM));
        ResponseTask task = taskFor("RT-1");
        report.addTask(task);
        task.addUpdate(new StatusUpdate("SU-1", ResponseStatus.IN_PROGRESS, "On site", "crew"));

        assertThrows(IllegalStateException.class, () -> report.resolve());
        assertEquals(ReportStatus.DISPATCHED, report.currentStatus(),
                "A refused transition must leave the status unchanged.");
    }

    @Test
    @DisplayName("A report cannot be assessed twice")
    void secondAssessmentRefused() {
        report.attachAssessment(assessmentOf(Severity.HIGH));
        assertThrows(IllegalStateException.class,
                () -> report.attachAssessment(assessmentOf(Severity.LOW)));
    }

    @Test
    @DisplayName("Work cannot be dispatched before the report is assessed")
    void dispatchRefusedBeforeAssessment() {
        assertThrows(IllegalStateException.class, () -> report.addTask(taskFor("RT-1")));
    }

    @Test
    @DisplayName("A report cannot be resolved straight from SUBMITTED")
    void resolveRefusedFromSubmitted() {
        assertThrows(IllegalStateException.class, () -> report.resolve());
    }

    @Test
    @DisplayName("T26: every attribute reads back the value it was given")
    void allAttributesRoundTrip() {
        Reporter reporter = new Reporter("RP-01", "J. Perera", "0412345678");
        Location location = new Location("5 Queen Street", -37.8, 144.9);
        DisasterReport full = new DisasterReport("DR-0002", DisasterType.FLOOD,
                location, Severity.SEVERE, "Water rising", reporter);

        assertEquals("DR-0002", full.getReferenceNumber());
        assertEquals(DisasterType.FLOOD, full.getType());
        assertEquals(location, full.getLocation());
        assertEquals(Severity.SEVERE, full.getReportedSeverity());
        assertEquals("Water rising", full.getDescription());
        assertEquals(reporter, full.getReporter());
        assertNotNull(full.getReportedAt());
        assertFalse(full.isAnonymous());
    }

    @Test
    @DisplayName("A report lodged with no reporter is anonymous")
    void reportWithoutReporterIsAnonymous() {
        assertTrue(report.isAnonymous());
        assertNull(report.getReporter());
    }

    @Test
    @DisplayName("T63: a description of 500 characters is accepted and 501 is refused")
    void descriptionLengthBoundary() {
        String atLimit = "x".repeat(DrsConstants.DESCRIPTION_MAX_LENGTH);
        String overLimit = "x".repeat(DrsConstants.DESCRIPTION_MAX_LENGTH + 1);

        report.setDescription(atLimit);
        assertEquals(atLimit, report.getDescription());
        assertThrows(IllegalArgumentException.class, () -> report.setDescription(overLimit));
    }

    @Test
    @DisplayName("The task list cannot be added to directly")
    void taskListIsUnmodifiable() {
        report.attachAssessment(assessmentOf(Severity.LOW));
        assertThrows(UnsupportedOperationException.class,
                () -> report.getTasks().add(taskFor("RT-99")));
    }
}
