package com.cqu.drs.db;

import java.util.List;

/**
 * The tables the resource allocation feature adds.
 *
 * <h2>Why the available count is a column and not a query</h2>
 * The number of units free could be worked out by subtracting the held
 * allocations from the total every time it is wanted. It is a column because
 * the feature exists to demonstrate what happens when two coordinators want
 * the last crew at the same moment, and a column is one row that InnoDB can
 * lock. A derived figure would have to be read, decided upon, and written,
 * with nothing stopping another transaction doing the same between the read
 * and the write.
 *
 * <p>The cost of storing it is that the two numbers can disagree, so the
 * database is told the rule rather than trusted to be handed consistent
 * values: {@code chk_resources_available_within_total} refuses any row where
 * the available count is negative or larger than the total. A fault in the
 * Java would then be stopped at the point it tried to write, not discovered
 * later as a pool holding minus one crew.
 *
 * <h2>Why allocations are rows that stay</h2>
 * Releasing units sets a status and a time; it does not delete the row. The
 * allocation is the record that a coordinator committed those units to that
 * disaster at that moment, and the audit trail refers to it. Deleting it on
 * release would destroy the evidence that the commitment was ever made, which
 * is exactly what somebody reviewing the response afterwards needs.
 *
 * <h2>Foreign keys restrict rather than cascade</h2>
 * The same decision the core schema made, for the same reason: a check
 * constraint on a column covered by a foreign key that can alter that column
 * is refused by MySQL. Beyond the mechanics, a disaster report with
 * allocations against it should not be removable in the first place.
 */
public final class ResourceSchema implements SchemaModule {

    /**
     * The pools themselves, one row per kind of thing a department holds.
     *
     * unit_label is stored rather than derived from the type so that a
     * department can call its pool what it actually calls it. The screens fall
     * back to the type's own noun when it is null.
     */
    private static final String RESOURCES = """
            CREATE TABLE IF NOT EXISTS resources (
              resource_id      VARCHAR(40)  NOT NULL,
              department_id    VARCHAR(20)  NOT NULL,
              type             VARCHAR(20)  NOT NULL,
              name             VARCHAR(100) NOT NULL,
              total_units      INT          NOT NULL,
              available_units  INT          NOT NULL,
              PRIMARY KEY (resource_id),
              UNIQUE KEY uq_resources_department_name (department_id, name),
              KEY idx_resources_department (department_id),
              CONSTRAINT fk_resources_department
                FOREIGN KEY (department_id) REFERENCES departments (department_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
              CONSTRAINT chk_resources_type
                CHECK (type IN ('PERSONNEL', 'VEHICLE', 'EQUIPMENT', 'MEDICAL', 'SHELTER')),
              CONSTRAINT chk_resources_total_not_negative
                CHECK (total_units >= 0),
              CONSTRAINT chk_resources_available_within_total
                CHECK (available_units >= 0 AND available_units <= total_units)
            )""";

    /**
     * What has been committed to which disaster.
     *
     * The index on (resource_id, status) is what makes the question "how much
     * of this pool is out" cheap, which is the question asked whenever the
     * stored available count has to be checked against the allocations it is
     * meant to agree with.
     */
    private static final String RESOURCE_ALLOCATIONS = """
            CREATE TABLE IF NOT EXISTS resource_allocations (
              allocation_id     VARCHAR(40)  NOT NULL,
              resource_id       VARCHAR(40)  NOT NULL,
              reference_number  VARCHAR(12)  NOT NULL,
              units             INT          NOT NULL,
              status            VARCHAR(10)  NOT NULL,
              requested_by      VARCHAR(20)  NOT NULL,
              allocated_at      DATETIME(6)  NOT NULL,
              released_by       VARCHAR(20)  NULL,
              released_at       DATETIME(6)  NULL,
              PRIMARY KEY (allocation_id),
              KEY idx_allocations_report (reference_number),
              KEY idx_allocations_resource_status (resource_id, status),
              CONSTRAINT fk_allocations_resource
                FOREIGN KEY (resource_id) REFERENCES resources (resource_id)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
              CONSTRAINT fk_allocations_report
                FOREIGN KEY (reference_number)
                REFERENCES disaster_reports (reference_number)
                ON DELETE RESTRICT ON UPDATE RESTRICT,
              CONSTRAINT chk_allocations_units_positive
                CHECK (units > 0),
              CONSTRAINT chk_allocations_status
                CHECK (status IN ('HELD', 'RELEASED')),
              CONSTRAINT chk_allocations_release_recorded
                CHECK ((status = 'HELD' AND released_at IS NULL)
                       OR (status = 'RELEASED' AND released_at IS NOT NULL))
            )""";

    @Override
    public String name() {
        return "resource allocation";
    }

    /**
     * {@inheritDoc}
     *
     * Pools before allocations, because an allocation carries a foreign key
     * into the pool it came from.
     */
    @Override
    public List<String> tableDefinitions() {
        return List.of(RESOURCES, RESOURCE_ALLOCATIONS);
    }
}
