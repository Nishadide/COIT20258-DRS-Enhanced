package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Work dispatched to one department in response to one disaster. The task
 * keeps the full history of status updates it has received, and its own
 * status always reflects the most recent of them.
 */
public class ResponseTask {

    private String taskId;
    private String departmentId;
    private String departmentName;
    private LocalDateTime dispatchedAt;
    private ResponseStatus status;
    private boolean notified;
    private List<StatusUpdate> history;

    /**
     * Creates a task dispatched to a department, stamped with the current time.
     *
     * @param taskId     unique identifier
     * @param department the department the work is dispatched to
     */
    public ResponseTask(String taskId, Department department) {
        if (department == null) {
            throw new IllegalArgumentException("A response task must name a department.");
        }
        this.taskId = taskId;
        this.departmentId = department.getDepartmentId();
        this.departmentName = department.getName();
        this.dispatchedAt = LocalDateTime.now();
        this.status = ResponseStatus.DISPATCHED;
        this.notified = false;
        this.history = new ArrayList<>();
    }

    /** No-argument constructor required by Gson. */
    protected ResponseTask() {
        this.history = new ArrayList<>();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(LocalDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public void setStatus(ResponseStatus status) {
        this.status = status;
    }

    /**
     * Reports whether the dispatch notice reached the department's system.
     * A false value means the notice failed and the coordinator was asked to
     * follow up through another channel.
     *
     * @return true when the department was notified successfully
     */
    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    /**
     * The history of updates, oldest first. The list returned cannot be
     * modified, so the only way to add an update is through addUpdate.
     *
     * @return an unmodifiable view of the history
     */
    public List<StatusUpdate> getHistory() {
        if (history == null) {
            history = new ArrayList<>();
        }
        return Collections.unmodifiableList(history);
    }

    /**
     * Records an update against this task and moves the task to the status the
     * update carries.
     *
     * @param update the update to record
     * @throws IllegalStateException    if the task is already complete
     * @throws IllegalArgumentException if the update is null
     */
    public void addUpdate(StatusUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("Cannot record a null update.");
        }
        if (isComplete()) {
            throw new IllegalStateException(
                    "Task " + taskId + " is already complete and cannot be updated.");
        }
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(update);
        this.status = update.getStatus();
    }

    /**
     * Reports whether the work on this task has finished.
     *
     * @return true when the current status is COMPLETE
     */
    public boolean isComplete() {
        return status == ResponseStatus.COMPLETE;
    }

    @Override
    public String toString() {
        return taskId + " to " + departmentName + ", " + status.getLabel()
                + (notified ? "" : " [not notified]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResponseTask)) {
            return false;
        }
        return Objects.equals(taskId, ((ResponseTask) o).taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }
}
