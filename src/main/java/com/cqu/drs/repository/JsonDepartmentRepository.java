package com.cqu.drs.repository;

import com.cqu.drs.model.Department;
import com.cqu.drs.model.SeedData;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JSON-file implementation. Stores the responding departments. The eight departments named in the case
 * study are seeded the first time the system runs, so that the coordinator has
 * somewhere to dispatch work to without any setup.
 */
public class JsonDepartmentRepository implements DepartmentRepository {

    private static final String FILE_NAME = "departments.json";
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Department>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates a repository backed by the store given.
     *
     * @param store the data store to read and write through
     */
    public JsonDepartmentRepository(JsonDataStore store) {
        this.store = store;
    }

    /**
     * Writes the eight departments named in the case study, but only when none
     * are held yet. This is called once at start-up rather than from the
     * constructor, so that creating a repository has no side effect on disk.
     *
     * @return true when the departments were seeded on this call
     */
    @Override
    public boolean seedIfEmpty() {
        if (!store.load(FILE_NAME, LIST_TYPE).isEmpty()) {
            return false;
        }
        seed();
        return true;
    }

    /**
     * Writes the eight departments named in the case study.
     */
    private void seed() {
        store.store(FILE_NAME, new ArrayList<>(SeedData.departments()));
    }

    /**
     * Every department held.
     *
     * @return the departments
     */
    @Override
    public List<Department> findAll() {
        return store.load(FILE_NAME, LIST_TYPE);
    }

    /**
     * Only the departments currently able to take work.
     *
     * @return the available departments
     */
    @Override
    public List<Department> findAvailable() {
        List<Department> available = new ArrayList<>();
        for (Department d : findAll()) {
            if (d.isAvailable()) {
                available.add(d);
            }
        }
        return available;
    }

    /**
     * Finds one department by its identifier.
     *
     * @param departmentId the identifier to look for
     * @return the department, or an empty Optional when none matches
     */
    @Override
    public Optional<Department> findById(String departmentId) {
        if (departmentId == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(d -> departmentId.equals(d.getDepartmentId()))
                .findFirst();
    }

    /**
     * Saves a department, replacing any earlier version of the same one.
     *
     * @param department the department to save
     */
    @Override
    public void save(Department department) {
        if (department == null) {
            throw new IllegalArgumentException("Cannot save a null department.");
        }
        List<Department> all = findAll();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (department.getDepartmentId().equals(all.get(i).getDepartmentId())) {
                all.set(i, department);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            all.add(department);
        }
        store.store(FILE_NAME, all);
    }
}
