package com.cqu.drs.service;

import com.cqu.drs.model.Department;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.ResponseStatus;
import com.cqu.drs.model.ResponseTask;
import com.cqu.drs.model.StatusUpdate;

import java.util.List;
import java.util.Optional;

/**
 * Dispatching work to departments and following what they do with it.
 */
public interface CoordinationOperations {

    /**
     * Dispatches a disaster to several departments at once.
     *
     * Takes the whole selection rather than one department at a time, so that
     * the batch dispatch built in Assessment 2 remains one operation after the
     * split into tiers instead of becoming one request per department.
     *
     * @param reference     the disaster being dispatched
     * @param departments   the departments chosen
     * @param dispatchedBy  the coordinator's username
     * @return the tasks created, each recording whether its notice got through
     */
    List<ResponseTask> allocate(String reference, List<Department> departments,
                                String dispatchedBy);

    /**
     * Records progress on a dispatched task.
     *
     * A responder may do this only for their own department. The check is made
     * on the server, where a modified client cannot reach it.
     *
     * @param reference the disaster the task belongs to
     * @param taskId    the task being updated
     * @param status    the status reached
     * @param note      optional note
     * @param updatedBy the username recording it
     * @return the update recorded
     */
    StatusUpdate updateStatus(String reference, String taskId, ResponseStatus status,
                              String note, String updatedBy);

    /**
     * The tasks dispatched to one department.
     *
     * @param departmentId the department
     * @return the tasks
     */
    List<ResponseTask> findTasksForDepartment(String departmentId);

    /**
     * The disaster a task belongs to.
     *
     * @param taskId the task
     * @return the report, or an empty Optional
     */
    Optional<DisasterReport> findReportForTask(String taskId);

    /**
     * The departments currently able to take work.
     *
     * @return the available departments
     */
    List<Department> availableDepartments();
}
