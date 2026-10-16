package com.cqu.drs.repository;

import com.cqu.drs.model.Resource;
import com.cqu.drs.model.ResourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * The pools each department starts with.
 *
 * <h2>Why the numbers are small</h2>
 * They are small on purpose. A pool of forty crews can never be exhausted by
 * two people clicking in a demonstration, and a feature whose interesting
 * behaviour cannot be reached is a feature nobody can see working. These
 * numbers let the last unit be reached in a few actions, which is what the
 * concurrency test and the demonstration both need.
 *
 * <p>Kept beside the repositories rather than in the model with the
 * departments, because unlike the eight departments these are not part of the
 * case study: they are a plausible starting position that an administrator
 * would change.
 */
final class ResourceSeed {

    private static final Object[][] POOLS = {
        {"DEP-01", ResourceType.PERSONNEL, "Fire crew", 4},
        {"DEP-01", ResourceType.VEHICLE, "Heavy pumper", 2},
        {"DEP-02", ResourceType.MEDICAL, "Emergency bed", 6},
        {"DEP-02", ResourceType.VEHICLE, "Ambulance", 3},
        {"DEP-03", ResourceType.PERSONNEL, "Line crew", 3},
        {"DEP-04", ResourceType.VEHICLE, "Evacuation bus", 2},
        {"DEP-05", ResourceType.VEHICLE, "Clearance truck", 2},
        {"DEP-06", ResourceType.EQUIPMENT, "Water tanker", 2},
        {"DEP-07", ResourceType.SHELTER, "Hall place", 120},
        {"DEP-08", ResourceType.PERSONNEL, "Patrol unit", 4}
    };

    /** Not instantiable: this class holds one list. */
    private ResourceSeed() {
    }

    /**
     * The starting pools, every unit available.
     *
     * Identifiers are derived from the department and the name rather than
     * generated, so that seeding a database twice produces the same rows
     * rather than a second set of pools with different identifiers.
     *
     * @return the resources
     */
    static List<Resource> pools() {
        List<Resource> resources = new ArrayList<>(POOLS.length);
        for (Object[] row : POOLS) {
            String departmentId = (String) row[0];
            ResourceType type = (ResourceType) row[1];
            String name = (String) row[2];
            int units = (Integer) row[3];

            String id = "RS-" + departmentId.substring(departmentId.length() - 2)
                    + "-" + name.toLowerCase().replace(' ', '-');
            resources.add(new Resource(id, departmentId, type, name, units));
        }
        return resources;
    }
}
