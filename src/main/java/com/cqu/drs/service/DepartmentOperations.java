package com.cqu.drs.service;

import com.cqu.drs.model.Department;

import java.util.List;
import java.util.Optional;

/**
 * The responding departments, and whether they can take work.
 *
 * <h2>Why this exists rather than the screens using the repository</h2>
 * In DRS-Initial the account management screen reached the department
 * repository directly, changed a department, saved it, and then wrote its own
 * audit entry. A screen in a three-tier client cannot hold a repository at
 * all, and the audit entry has to be written by the server, which is the only
 * party that can vouch for what actually happened.
 *
 * <p>So setting availability became one operation that does both. That is also
 * better than what it replaced: the saving and the recording can no longer
 * come apart, which they could when a screen did them in sequence.
 */
public interface DepartmentOperations {

    /**
     * Every department, available or not.
     *
     * @return the departments
     */
    List<Department> findAll();

    /**
     * The departments currently able to take work.
     *
     * @return the available departments
     */
    List<Department> findAvailable();

    /**
     * Finds one department.
     *
     * @param departmentId the identifier
     * @return the department, or an empty Optional
     */
    Optional<Department> findById(String departmentId);

    /**
     * Marks a department available or unavailable, and records who did it.
     *
     * @param departmentId the department
     * @param available    whether it can take work
     * @param changedBy    the username making the change
     * @return the department as it now stands
     */
    Department setAvailability(String departmentId, boolean available, String changedBy);
}
