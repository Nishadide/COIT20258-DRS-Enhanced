package com.cqu.drs.service;

import com.cqu.drs.model.Alert;
import com.cqu.drs.model.AlertType;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.Severity;
import com.cqu.drs.repository.AlertRepository;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonAlertRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDepartmentRepository;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T70 to T75 and T38 to T40 at service level: dispatching work, the
 * failure path when a department cannot be reached, ordering the queue by
 * priority, and issuing alerts.
 */
@DisplayName("Coordination, assessment and alerting")
class CoordinationServiceTest {

    private static final String ADDRESS = "12 Bridge Street, Melbourne";

    private ReportService reportService;
    private AssessmentService assessmentService;
    private CoordinationService coordinationService;
    private AlertService alertService;
    private DepartmentRepository departmentRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        JsonDataStore store = new JsonDataStore(tempDir);
        DisasterReportRepository reportRepository = new JsonDisasterReportRepository(store);
        departmentRepository = new JsonDepartmentRepository(store);
        departmentRepository.seedIfEmpty();

        AuditService auditService = new AuditService(new JsonAuditRepository(store));
        reportService = new ReportService(reportRepository, auditService);
        assessmentService = new AssessmentService(reportRepository, auditService);
        coordinationService = new CoordinationService(
                reportRepository, departmentRepository, auditService);
        alertService = new AlertService(reportRepository,
                new JsonAlertRepository(store), auditService);
    }

    private String submitAndAssess(Severity severity) {
        String reference = reportService.submitReport(DisasterType.FIRE, ADDRESS,
                null, null, severity, null, null);
        assessmentService.assess(reference, severity, "Confirmed", "coordinator");
        return reference;
    }

    @Test
    @DisplayName("T70: the queue puts P1 ahead of P2 and P3")
    void queueOrdersByPriority() {
        submitAndAssess(Severity.MEDIUM);
        submitAndAssess(Severity.SEVERE);
        submitAndAssess(Severity.HIGH);

        List<DisasterReport> queue = assessmentService.queueByPriority();
        assertEquals(3, queue.size());
        assertEquals(Severity.SEVERE, queue.get(0).getAssessment().getSeverity());
        assertEquals(Severity.HIGH, queue.get(1).getAssessment().getSeverity());
        assertEquals(Severity.MEDIUM, queue.get(2).getAssessment().getSeverity());
    }

    @Test
    @DisplayName("T71: the queue is empty when nothing has been assessed")
    void queueEmptyWhenNoneAssessed() {
        reportService.submitReport(DisasterType.FIRE, ADDRESS, null, null,
                Severity.HIGH, null, null);
        assertTrue(assessmentService.queueByPriority().isEmpty());
        assertEquals(1, assessmentService.awaitingAssessment().size());
    }

    @Test
    @DisplayName("T72: one task is created for each department allocated")
    void allocateCreatesTaskPerDepartment() {
        String reference = submitAndAssess(Severity.HIGH);
        List<Department> two = departmentRepository.findAll().subList(0, 2);

        List<ResponseTask> tasks = coordinationService.allocate(reference, two, "coordinator");

        assertEquals(2, tasks.size());
        assertNotNull(tasks.get(0).getDispatchedAt());
        assertEquals(2, assessmentService.findByReference(reference)
                .orElseThrow().getTasks().size());
    }

    @Test
    @DisplayName("T73: allocating no departments is refused")
    void allocateRefusesEmptyList() {
        String reference = submitAndAssess(Severity.HIGH);
        assertThrows(IllegalArgumentException.class, () ->
                coordinationService.allocate(reference, Collections.emptyList(), "coordinator"));
        assertTrue(assessmentService.findByReference(reference)
                .orElseThrow().getTasks().isEmpty());
    }

    @Test
    @DisplayName("T74: an available department is notified successfully")
    void notifySucceedsWhenAvailable() {
        String reference = submitAndAssess(Severity.HIGH);
        Department available = departmentRepository.findAll().get(0);

        List<ResponseTask> tasks = coordinationService.allocate(
                reference, List.of(available), "coordinator");
        assertTrue(tasks.get(0).isNotified());
    }

    @Test
    @DisplayName("T75: an unavailable department leaves the task unnotified")
    void notifyFailsAndAuditsWhenUnavailable() {
        String reference = submitAndAssess(Severity.HIGH);
        Department department = departmentRepository.findAll().get(0);
        department.setAvailable(false);
        departmentRepository.save(department);

        List<ResponseTask> tasks = coordinationService.allocate(
                reference, List.of(department), "coordinator");
        assertFalse(tasks.get(0).isNotified(),
                "A failed notice must be visible, not assumed successful.");
    }

    @Test
    @DisplayName("A status update is recorded against the task")
    void updateStatusRecordsHistory() {
        String reference = submitAndAssess(Severity.HIGH);
        Department department = departmentRepository.findAll().get(0);
        List<ResponseTask> tasks = coordinationService.allocate(
                reference, List.of(department), "coordinator");

        coordinationService.updateStatus(reference, tasks.get(0).getTaskId(),
                ResponseStatus.IN_PROGRESS, "Crew on site", "crew");

        DisasterReport stored = assessmentService.findByReference(reference).orElseThrow();
        assertEquals(ResponseStatus.IN_PROGRESS, stored.getTasks().get(0).getStatus());
        assertEquals(1, stored.getTasks().get(0).getHistory().size());
    }

    @Test
    @DisplayName("A report resolves once every task is complete")
    void resolveAfterAllTasksComplete() {
        String reference = submitAndAssess(Severity.LOW);
        Department department = departmentRepository.findAll().get(0);
        List<ResponseTask> tasks = coordinationService.allocate(
                reference, List.of(department), "coordinator");

        coordinationService.updateStatus(reference, tasks.get(0).getTaskId(),
                ResponseStatus.COMPLETE, "Finished", "crew");
        assessmentService.resolve(reference, "coordinator");

        assertTrue(assessmentService.queueByPriority().isEmpty(),
                "A resolved report should leave the active queue.");
    }

    @Test
    @DisplayName("A report will not resolve while a task is still open")
    void resolveRefusedWhileWorkOpen() {
        String reference = submitAndAssess(Severity.LOW);
        Department department = departmentRepository.findAll().get(0);
        coordinationService.allocate(reference, List.of(department), "coordinator");

        assertThrows(IllegalStateException.class,
                () -> assessmentService.resolve(reference, "coordinator"));
    }

    @Test
    @DisplayName("Only the open tasks of one department are returned")
    void findTasksForDepartmentFiltersCorrectly() {
        String reference = submitAndAssess(Severity.HIGH);
        List<Department> two = departmentRepository.findAll().subList(0, 2);
        coordinationService.allocate(reference, two, "coordinator");

        assertEquals(1, coordinationService.findTasksForDepartment(
                two.get(0).getDepartmentId()).size());
        assertTrue(coordinationService.findTasksForDepartment("DEP-99").isEmpty());
    }

    @Test
    @DisplayName("T38: an alert is delivered when the channel is reachable")
    void alertDeliveredWhenChannelUp() {
        String reference = submitAndAssess(Severity.SEVERE);
        Alert alert = alertService.issue(reference, AlertType.EVACUATION,
                "Zone 4", "Leave by the northern road.", "coordinator");

        assertTrue(alert.isDelivered());
        assertEquals(1, alertService.findAll().size());
    }

    @Test
    @DisplayName("T39: a failed broadcast is recorded rather than assumed successful")
    void alertNotDeliveredWhenChannelDown() {
        String reference = submitAndAssess(Severity.SEVERE);
        alertService.setChannelReachable(false);

        Alert alert = alertService.issue(reference, AlertType.WARNING,
                "Zone 4", "Stay indoors.", "coordinator");
        assertFalse(alert.isDelivered());
    }

    @Test
    @DisplayName("Only a severe assessment prompts an evacuation")
    void evacuationOfferedOnlyForSevere() {
        assertTrue(alertService.warrantsEvacuation(Severity.SEVERE));
        assertFalse(alertService.warrantsEvacuation(Severity.HIGH));
    }

    @Test
    @DisplayName("Assessing a report that does not exist is refused")
    void assessUnknownReferenceRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                assessmentService.assess("DR-9999", Severity.HIGH, null, "coordinator"));
    }

    @Test
    @DisplayName("T98: switching a department to unavailable is stored and can be undone")
    void availabilityTogglePersistsBothWays() {
        Department department = departmentRepository.findById("DEP-02").orElseThrow();
        assertTrue(department.isAvailable());

        department.setAvailable(false);
        departmentRepository.save(department);
        assertFalse(departmentRepository.findById("DEP-02").orElseThrow().isAvailable());

        Department stored = departmentRepository.findById("DEP-02").orElseThrow();
        stored.setAvailable(true);
        departmentRepository.save(stored);
        assertTrue(departmentRepository.findById("DEP-02").orElseThrow().isAvailable());
    }

    @Test
    @DisplayName("T99: saving a department keeps it in its place in the list")
    void savingDepartmentKeepsItsPosition() {
        List<Department> before = departmentRepository.findAll();
        int position = -1;
        for (int i = 0; i < before.size(); i++) {
            if ("DEP-03".equals(before.get(i).getDepartmentId())) {
                position = i;
                break;
            }
        }

        Department department = departmentRepository.findById("DEP-03").orElseThrow();
        department.setAvailable(false);
        departmentRepository.save(department);

        List<Department> after = departmentRepository.findAll();
        assertEquals(before.size(), after.size(),
                "Saving must not add or drop a department.");
        assertEquals("DEP-03", after.get(position).getDepartmentId(),
                "A saved department must stay where it was, so the list does not reorder.");
    }

    @Test
    @DisplayName("T100: an unavailable department is hidden from the available list "
            + "but is still on record")
    void unavailableDepartmentStillOnRecord() {
        Department department = departmentRepository.findById("DEP-04").orElseThrow();
        department.setAvailable(false);
        departmentRepository.save(department);

        assertEquals(8, departmentRepository.findAll().size());
        assertEquals(7, coordinationService.availableDepartments().size());
        assertTrue(coordinationService.availableDepartments().stream()
                        .noneMatch(d -> "DEP-04".equals(d.getDepartmentId())),
                "An unavailable department must not be offered as available.");
    }

    @Test
    @DisplayName("T101: work dispatched to an unavailable department is still created "
            + "and flagged for follow-up")
    void dispatchToUnavailableDepartmentIsFlagged() {
        String reference = submitAndAssess(Severity.HIGH);

        Department department = departmentRepository.findById("DEP-05").orElseThrow();
        department.setAvailable(false);
        departmentRepository.save(department);

        List<ResponseTask> created = coordinationService.allocate(reference,
                List.of(departmentRepository.findById("DEP-05").orElseThrow()),
                "coordinator");

        assertEquals(1, created.size(),
                "The task must still be created so the work is not lost.");
        assertFalse(created.get(0).isNotified(),
                "The notice to an unavailable department must be recorded as failed.");
    }

    @Test
    @DisplayName("T102: a department made available again receives its notice")
    void dispatchSucceedsAfterDepartmentMadeAvailableAgain() {
        Department department = departmentRepository.findById("DEP-06").orElseThrow();
        department.setAvailable(false);
        departmentRepository.save(department);

        Department restored = departmentRepository.findById("DEP-06").orElseThrow();
        restored.setAvailable(true);
        departmentRepository.save(restored);

        String reference = submitAndAssess(Severity.HIGH);
        List<ResponseTask> created = coordinationService.allocate(reference,
                List.of(departmentRepository.findById("DEP-06").orElseThrow()),
                "coordinator");

        assertTrue(created.get(0).isNotified(),
                "A department made available again must receive its notice.");
    }

    @Test
    @DisplayName("T103: a mixed dispatch reaches the available departments "
            + "and flags the rest")
    void mixedDispatchReachesAvailableDepartmentsOnly() {
        Department unavailable = departmentRepository.findById("DEP-07").orElseThrow();
        unavailable.setAvailable(false);
        departmentRepository.save(unavailable);

        String reference = submitAndAssess(Severity.SEVERE);
        List<ResponseTask> created = coordinationService.allocate(reference,
                List.of(departmentRepository.findById("DEP-01").orElseThrow(),
                        departmentRepository.findById("DEP-07").orElseThrow()),
                "coordinator");

        assertEquals(2, created.size());
        assertEquals(1, created.stream().filter(ResponseTask::isNotified).count(),
                "One unreachable department must not stop the other being reached.");
    }

    @Test
    @DisplayName("T104: a report cannot be resolved while a failed dispatch is open")
    void reportStaysOpenUntilFailedDispatchIsClosed() {
        Department unavailable = departmentRepository.findById("DEP-08").orElseThrow();
        unavailable.setAvailable(false);
        departmentRepository.save(unavailable);

        String reference = submitAndAssess(Severity.HIGH);
        coordinationService.allocate(reference,
                List.of(departmentRepository.findById("DEP-08").orElseThrow()),
                "coordinator");

        assertThrows(IllegalStateException.class,
                () -> assessmentService.resolve(reference, "coordinator"),
                "Work that is still open must stop the disaster being signed off.");
    }

    @Test
    @DisplayName("T105: recording a follow-up closes a failed dispatch "
            + "and lets the report be resolved")
    void followUpOnFailedDispatchAllowsResolution() {
        Department unavailable = departmentRepository.findById("DEP-08").orElseThrow();
        unavailable.setAvailable(false);
        departmentRepository.save(unavailable);

        String reference = submitAndAssess(Severity.HIGH);
        List<ResponseTask> created = coordinationService.allocate(reference,
                List.of(departmentRepository.findById("DEP-08").orElseThrow()),
                "coordinator");
        assertFalse(created.get(0).isNotified());

        coordinationService.updateStatus(reference, created.get(0).getTaskId(),
                ResponseStatus.COMPLETE, "Followed up by telephone.", "coordinator");

        assessmentService.resolve(reference, "coordinator");
        assertEquals("Resolved", assessmentService.findByReference(reference)
                .orElseThrow().currentStatus().getLabel());
    }
}
