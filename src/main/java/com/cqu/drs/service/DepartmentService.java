package com.cqu.drs.service;

import com.cqu.drs.model.Department;
import com.cqu.drs.repository.DepartmentRepository;

import java.util.List;
import java.util.Optional;

/**
 * The departments, and whether they can take work.
 *
 * <h2>Why this class exists now and did not before</h2>
 * In DRS-Initial the account management screen held the department repository
 * itself: it fetched a department, flipped its availability, saved it, and
 * then wrote an audit entry. Three steps, in a screen.
 *
 * <p>Neither of the outer two survives the move to three tiers. A screen in a
 * client has no repository to hold, and the audit entry cannot be written by
 * the client at all, because the server is the only party that can vouch for
 * what actually happened. So the three steps became one operation.
 *
 * <p>That is a better arrangement than the one it replaces, not merely a
 * necessary one. Saving the department and recording the change can no longer
 * come apart, which they could when a screen did them one after the other and
 * anything could happen in between.
 */
public class DepartmentService implements DepartmentOperations {

    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param departmentRepository where departments are stored
     * @param auditService         where the change is recorded
     */
    public DepartmentService(DepartmentRepository departmentRepository,
                             AuditService auditService) {
        if (departmentRepository == null || auditService == null) {
            throw new IllegalArgumentException(
                    "A department service needs a repository and an audit service.");
        }
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Override
    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    @Override
    public List<Department> findAvailable() {
        return departmentRepository.findAvailable();
    }

    @Override
    public Optional<Department> findById(String departmentId) {
        return departmentRepository.findById(departmentId);
    }

    @Override
    public Department setAvailability(String departmentId, boolean available,
                                      String changedBy) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No department holds the identifier " + departmentId + "."));

        if (department.isAvailable() == available) {
            // Nothing changed, so nothing is recorded. An audit trail full of
            // entries saying a value was set to what it already was is harder
            // to read, and makes the entries that matter harder to find.
            return department;
        }

        department.setAvailable(available);
        departmentRepository.save(department);

        auditService.record(changedBy, "Set " + department.getName() + " to "
                + (available ? "available" : "unavailable"));

        return department;
    }
}
