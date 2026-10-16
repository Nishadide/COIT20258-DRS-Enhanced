package com.cqu.drs.repository;

import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceAllocation;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores the resource pools in JSON files, for the local mode the application
 * runs in when no server is started.
 *
 * <h2>What this implementation cannot do, stated plainly</h2>
 * It makes allocation indivisible with a Java lock. That is sufficient here
 * and only here: local mode is one person at one keyboard in one process, so
 * there is nothing else to race against.
 *
 * <p>It would not be sufficient for the server, and this class is never used
 * there. A lock protects one process. Two servers, or a second copy of the
 * application opened on the same files, would each hold their own lock and
 * neither would see the other, so both could take the last crew. The MySQL
 * implementation does not have that limitation because it asks the database
 * to order the two transactions, and the database is the thing both processes
 * share.
 *
 * <p>This is the clearest demonstration in the project of why the feature
 * needed a database transaction rather than careful Java. The two
 * implementations satisfy the same interface and behave identically on one
 * machine; only one of them is still correct when a second client appears.
 */
public class JsonResourceRepository implements ResourceRepository {

    private static final String RESOURCES_FILE = "resources.json";
    private static final String ALLOCATIONS_FILE = "resource-allocations.json";

    private static final Type RESOURCE_LIST =
            new TypeToken<ArrayList<Resource>>() { }.getType();
    private static final Type ALLOCATION_LIST =
            new TypeToken<ArrayList<ResourceAllocation>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates the repository.
     *
     * @param store where the files are written
     */
    public JsonResourceRepository(JsonDataStore store) {
        if (store == null) {
            throw new IllegalArgumentException("A repository needs a data store.");
        }
        this.store = store;
    }

    @Override
    public synchronized List<Resource> findAll() {
        List<Resource> resources = store.load(RESOURCES_FILE, RESOURCE_LIST);
        resources.sort(Comparator.comparing(Resource::getDepartmentId)
                .thenComparing(Resource::getName));
        return resources;
    }

    @Override
    public synchronized List<Resource> findByDepartment(String departmentId) {
        List<Resource> matching = new ArrayList<>();
        for (Resource resource : findAll()) {
            if (resource.getDepartmentId() != null
                    && resource.getDepartmentId().equals(departmentId)) {
                matching.add(resource);
            }
        }
        return matching;
    }

    @Override
    public synchronized Optional<Resource> findById(String resourceId) {
        if (resourceId == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(resource -> resourceId.equals(resource.getResourceId()))
                .findFirst();
    }

    @Override
    public synchronized void save(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot save a null resource.");
        }
        List<Resource> all = store.load(RESOURCES_FILE, RESOURCE_LIST);
        all.removeIf(held -> held.getResourceId().equals(resource.getResourceId()));
        all.add(resource);
        store.store(RESOURCES_FILE, all);
    }

    @Override
    public synchronized boolean seedIfEmpty() {
        if (!store.load(RESOURCES_FILE, RESOURCE_LIST).isEmpty()) {
            return false;
        }
        store.store(RESOURCES_FILE, new ArrayList<>(ResourceSeed.pools()));
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * Synchronised on this repository, which orders two threads in this
     * process and nothing beyond it. See the note on the class.
     */
    @Override
    public synchronized ResourceAllocation allocate(String resourceId, String reference,
                                                    int units, String requestedBy) {
        if (units <= 0) {
            throw new IllegalArgumentException(
                    "The number of units requested must be at least one.");
        }

        Resource resource = findById(resourceId).orElseThrow(
                () -> new IllegalArgumentException(
                        "No resource holds the identifier " + resourceId + "."));

        if (resource.getAvailableUnits() < units) {
            throw new InsufficientResourcesException(resource.getName(), units,
                    resource.getAvailableUnits());
        }

        resource.claim(units);
        save(resource);

        ResourceAllocation allocation = new ResourceAllocation(
                "RA-" + UUID.randomUUID().toString().substring(0, 8),
                resource, reference, units, requestedBy);

        List<ResourceAllocation> allocations =
                store.load(ALLOCATIONS_FILE, ALLOCATION_LIST);
        allocations.add(allocation);
        store.store(ALLOCATIONS_FILE, allocations);

        return allocation;
    }

    @Override
    public synchronized ResourceAllocation release(String allocationId, String releasedBy) {
        List<ResourceAllocation> allocations =
                store.load(ALLOCATIONS_FILE, ALLOCATION_LIST);

        ResourceAllocation allocation = allocations.stream()
                .filter(held -> held.getAllocationId().equals(allocationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No allocation holds the identifier " + allocationId + "."));

        // Throws when it was already released, which is what stops the units
        // going back into the pool twice.
        allocation.markReleased(releasedBy);

        Resource resource = findById(allocation.getResourceId()).orElseThrow(
                () -> new IllegalStateException("The allocation refers to a resource "
                        + "that no longer exists."));
        resource.release(allocation.getUnits());
        save(resource);

        store.store(ALLOCATIONS_FILE, allocations);
        return allocation;
    }

    @Override
    public synchronized Optional<ResourceAllocation> findAllocationById(String allocationId) {
        if (allocationId == null) {
            return Optional.empty();
        }
        return store.load(ALLOCATIONS_FILE, ALLOCATION_LIST).stream()
                .filter(allocation -> allocationId.equals(
                        ((ResourceAllocation) allocation).getAllocationId()))
                .map(allocation -> (ResourceAllocation) allocation)
                .findFirst();
    }

    @Override
    public synchronized List<ResourceAllocation> findAllocationsForReport(String reference) {
        List<ResourceAllocation> matching = new ArrayList<>();
        for (ResourceAllocation allocation
                : store.<ResourceAllocation>load(ALLOCATIONS_FILE, ALLOCATION_LIST)) {
            if (allocation.getReferenceNumber() != null
                    && allocation.getReferenceNumber().equals(reference)) {
                matching.add(allocation);
            }
        }
        matching.sort(Comparator.comparing(ResourceAllocation::getAllocatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return matching;
    }

    @Override
    public synchronized List<ResourceAllocation> findHeldAllocations() {
        List<ResourceAllocation> held = new ArrayList<>();
        for (ResourceAllocation allocation
                : store.<ResourceAllocation>load(ALLOCATIONS_FILE, ALLOCATION_LIST)) {
            if (allocation.isHeld()) {
                held.add(allocation);
            }
        }
        held.sort(Comparator.comparing(ResourceAllocation::getAllocatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return held;
    }
}
