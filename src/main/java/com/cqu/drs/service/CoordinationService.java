package com.cqu.drs.service;

import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.StatusUpdate;
import com.cqu.drs.repository.DepartmentRepository;
import com.cqu.drs.repository.DisasterReportRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Allocates departments to an assessed disaster, sends them their work and
 * tracks what they report back. Whether a department was actually reached is
 * recorded rather than assumed, so a failed notification can be followed up.
 */
public class CoordinationService {

    private final DisasterReportRepository reportRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param reportRepository     where reports are stored
     * @param departmentRepository where departments are held
     * @param auditService         used to record dispatches and updates
     */
    public CoordinationService(DisasterReportRepository reportRepository,
                               DepartmentRepository departmentRepository,
                               AuditService auditService) {
        this.reportRepository = reportRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    /**
     * Dispatches work to one or more departments for an assessed disaster.
     * Each department receives its own task, and each is notified separately,
     * so one unreachable department does not stop the others.
     *
     * @param reference    the report being responded to
     * @param departments  the departments to allocate
     * @param dispatchedBy the username of the coordinator
     * @return the tasks created
     * @throws IllegalArgumentException when no department is given or the report is unknown
     */
    public List<ResponseTask> allocate(String reference, List<Department> departments,
                                       String dispatchedBy) {
        if (departments == null || departments.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one department must be allocated.");
        }
        DisasterReport report = reportRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No report holds the reference " + reference + "."));

        List<ResponseTask> created = new ArrayList<>();
        for (Department department : departments) {
            ResponseTask task = new ResponseTask(
                    DrsConstants.TASK_PREFIX + UUID.randomUUID().toString()
                            .substring(0, DrsConstants.SHORT_ID_LENGTH), department);
            report.addTask(task);
            notifyDepartment(task, department);
            created.add(task);
        }
        reportRepository.save(report);

        auditService.record(dispatchedBy, "Dispatched " + created.size()
                + " task(s) for " + reference);
        return created;
    }

    /**
     * Sends a dispatch notice to a department's system. The exchange is
     * simulated in this prototype, so the outcome follows from whether the
     * department is available, and the result is recorded on the task.
     *
     * @param task       the task being sent
     * @param department the department receiving it
     * @return true when the notice was accepted
     */
    public boolean notifyDepartment(ResponseTask task, Department department) {
        boolean reached = department != null && department.isAvailable();
        task.setNotified(reached);
        if (!reached) {
            auditService.record(null, "Dispatch notice to "
                    + (department == null ? "unknown department" : department.getName())
                    + " failed for task " + task.getTaskId()
                    + "; follow up through another channel");
        }
        return reached;
    }

    /**
     * Records an update against a task from the department doing the work.
     *
     * @param reference the report the task belongs to
     * @param taskId    the task being updated
     * @param status    the status being recorded
     * @param note      an optional note
     * @param updatedBy the username of the responder
     * @return the update recorded
     * @throws IllegalArgumentException when the report or task cannot be found
     */
    public StatusUpdate updateStatus(String reference, String taskId,
                                     ResponseStatus status, String note,
                                     String updatedBy) {
        DisasterReport report = reportRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No report holds the reference " + reference + "."));

        ResponseTask task = report.getTasks().stream()
                .filter(t -> t.getTaskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Report " + reference + " has no task " + taskId + "."));

        StatusUpdate update = new StatusUpdate(
                UUID.randomUUID().toString(), status, note, updatedBy);
        task.addUpdate(update);
        reportRepository.save(report);

        auditService.record(updatedBy, "Updated task " + taskId + " on "
                + reference + " to " + status);
        return update;
    }

    /**
     * The open tasks belonging to one department, across every report.
     *
     * @param departmentId the department whose work is wanted
     * @return the tasks dispatched to that department and not yet complete
     */
    public List<ResponseTask> findTasksForDepartment(String departmentId) {
        List<ResponseTask> tasks = new ArrayList<>();
        for (DisasterReport report : reportRepository.findAll()) {
            for (ResponseTask task : report.getTasks()) {
                if (task.getDepartmentId() != null
                        && task.getDepartmentId().equals(departmentId)
                        && !task.isComplete()) {
                    tasks.add(task);
                }
            }
        }
        return tasks;
    }

    /**
     * The report a task belongs to, which the response screen needs in order
     * to show the responder what the task is for.
     *
     * @param taskId the task to trace
     * @return the report holding it, or an empty Optional
     */
    public Optional<DisasterReport> findReportForTask(String taskId) {
        return reportRepository.findAll().stream()
                .filter(r -> r.getTasks().stream()
                        .anyMatch(t -> t.getTaskId().equals(taskId)))
                .findFirst();
    }

    /**
     * The departments currently able to take work.
     *
     * @return the available departments
     */
    public List<Department> availableDepartments() {
        return departmentRepository.findAvailable();
    }
}
