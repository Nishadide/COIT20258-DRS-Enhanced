package com.cqu.drs.model;

import java.util.Objects;

/**
 * One of the responding organisations the system coordinates with, such as
 * fire and emergency or the hospital service. A department lies outside the
 * system boundary; this class holds the details the DRS needs in order to
 * dispatch work to it and record the outcome.
 */
public class Department {

    private String departmentId;
    private String name;
    private String contactEmail;
    private boolean available;

    /**
     * Creates a department that is available for dispatch.
     *
     * @param departmentId unique identifier
     * @param name         the department's name
     * @param contactEmail the address dispatch notices are sent to
     */
    public Department(String departmentId, String name, String contactEmail) {
        this.departmentId = departmentId;
        this.name = name;
        this.contactEmail = contactEmail;
        this.available = true;
    }

    /** No-argument constructor required by Gson. */
    protected Department() {
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    /**
     * Reports whether this department can accept a dispatched task. A
     * department that is unavailable still receives the notice, but the
     * notification is recorded as having failed.
     *
     * @return true when the department is available
     */
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return name + (available ? "" : " [unavailable]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Department)) {
            return false;
        }
        return Objects.equals(departmentId, ((Department) o).departmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(departmentId);
    }
}
