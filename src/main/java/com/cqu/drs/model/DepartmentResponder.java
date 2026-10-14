package com.cqu.drs.model;

/**
 * A representative of one responding department. A responder sees and updates
 * only the tasks dispatched to the department they belong to.
 */
public class DepartmentResponder extends User {

    private String departmentId;

    /**
     * Creates a responder.
     *
     * @param userId        unique identifier
     * @param username      login name
     * @param plainPassword initial password
     * @param fullName      the person's name
     * @param departmentId  the department this responder belongs to
     */
    public DepartmentResponder(String userId, String username, String plainPassword,
                               String fullName, String departmentId) {
        super(userId, username, plainPassword, fullName, UserRole.RESPONDER);
        this.departmentId = departmentId;
    }

    /** No-argument constructor required by Gson. */
    protected DepartmentResponder() {
        super();
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    /**
     * Reports whether a task belongs to this responder's department, which is
     * the check that stops one department updating another's work.
     *
     * @param task the task being considered
     * @return true when the task was dispatched to this responder's department
     */
    public boolean canUpdate(ResponseTask task) {
        return task != null
                && task.getDepartmentId() != null
                && task.getDepartmentId().equals(departmentId);
    }

    @Override
    public String toString() {
        return super.toString() + " [department " + departmentId + "]";
    }
}
