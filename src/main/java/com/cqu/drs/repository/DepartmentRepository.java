package com.cqu.drs.repository;

import com.cqu.drs.model.Department;

import java.util.List;
import java.util.Optional;

/**
 * Stores the responding departments. The eight departments named in the case
 * study are seeded the first time the system runs, so that a coordinator has
 * somewhere to dispatch work to without any setup.
 */
public interface DepartmentRepository {

    /**
     * Writes the departments named in the case study, but only when none are
     * held yet.
     *
     * @return true when the departments were seeded on this call
     */
    boolean seedIfEmpty();

    /**
     * Every department held, available or not.
     *
     * @return the departments
     */
    List<Department> findAll();

    /**
     * Only the departments currently able to take work.
     *
     * @return the available departments
     */
    List<Department> findAvailable();

    /**
     * Finds one department by its identifier.
     *
     * @param departmentId the identifier to look for
     * @return the department, or an empty Optional when none matches
     */
    Optional<Department> findById(String departmentId);

    /**
     * Saves a department, replacing any earlier version of the same one and
     * leaving it in its existing position in the list.
     *
     * @param department the department to save
     */
    void save(Department department);
}
