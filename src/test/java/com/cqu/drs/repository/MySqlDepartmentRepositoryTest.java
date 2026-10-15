package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Department;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T238 to T244: the responding departments in MySQL.
 *
 * The seeded identifiers matter more than they look. Response tasks migrated
 * from Assessment 2 refer to DEP-01 through DEP-08, and a foreign key means a
 * task pointing at a department that does not exist cannot be stored at all.
 * If this seed drifted from the one DRS-Initial used, the data migration would
 * fail rather than quietly produce something wrong, which is the better
 * failure but still a failure.
 */
@DisplayName("MySqlDepartmentRepository")
class MySqlDepartmentRepositoryTest {

    private String originalDatabaseName;
    private ConnectionFactory connections;
    private MySqlDepartmentRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        originalDatabaseName = DatabaseTestSupport.useTestDatabase();
        connections = DatabaseTestSupport.connectOrSkip();
        DatabaseTestSupport.clearAll(connections);
        repository = new MySqlDepartmentRepository(connections);
    }

    @AfterEach
    void tearDown() {
        DatabaseTestSupport.restoreDatabaseName(originalDatabaseName);
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T238: seeding an empty table creates the eight departments")
    void seedingCreatesTheEightDepartments() {
        assertTrue(repository.seedIfEmpty());

        List<Department> all = repository.findAll();
        assertEquals(8, all.size());
        assertEquals("DEP-01", all.get(0).getDepartmentId());
        assertEquals("Fire and Emergency", all.get(0).getName());
        assertEquals("DEP-08", all.get(7).getDepartmentId());
        assertEquals("Law Enforcement", all.get(7).getName());
    }

    @Test
    @DisplayName("T239: the seeded identifiers match the ones DRS-Initial used")
    void seededIdentifiersMatchAssessmentTwo() {
        // Response tasks migrated from Assessment 2 point at these. A foreign
        // key means a mismatch here would stop the migration dead.
        repository.seedIfEmpty();

        List<Department> all = repository.findAll();
        for (int i = 0; i < 8; i++) {
            assertEquals(String.format("DEP-%02d", i + 1), all.get(i).getDepartmentId());
        }
    }

    @Test
    @DisplayName("T240: seeding a table that already holds departments changes nothing")
    void seedingIsSkippedWhenNotEmpty() {
        repository.seedIfEmpty();
        repository.save(new Department("DEP-01", "Renamed by an administrator",
                "new@fire.gov.au"));

        assertFalse(repository.seedIfEmpty(),
                "A second seeding must report that it did nothing.");

        assertEquals(8, repository.findAll().size());
        assertEquals("Renamed by an administrator",
                repository.findById("DEP-01").orElseThrow().getName(),
                "A second seeding must not overwrite a change an administrator made.");
    }

    @Test
    @DisplayName("T241: a department is seeded available")
    void departmentsAreSeededAvailable() {
        repository.seedIfEmpty();

        assertEquals(8, repository.findAvailable().size());
        assertTrue(repository.findById("DEP-03").orElseThrow().isAvailable());
    }

    @Test
    @DisplayName("T242: marking a department unavailable removes it from the available list")
    void availabilityIsStored() {
        repository.seedIfEmpty();

        Department hospital = repository.findById("DEP-02").orElseThrow();
        hospital.setAvailable(false);
        repository.save(hospital);

        assertFalse(repository.findById("DEP-02").orElseThrow().isAvailable());
        assertEquals(7, repository.findAvailable().size());
        assertEquals(8, repository.findAll().size(),
                "An unavailable department is still a department.");
    }

    @Test
    @DisplayName("T243: availability survives being read back, rather than resetting")
    void availabilityIsNotResetOnRead() {
        // The constructor marks a new department available, which is right for
        // a new one and wrong for one being read back. This is the test that
        // would have caught that mistake.
        repository.seedIfEmpty();

        Department department = repository.findById("DEP-05").orElseThrow();
        department.setAvailable(false);
        repository.save(department);

        for (Department each : repository.findAll()) {
            if ("DEP-05".equals(each.getDepartmentId())) {
                assertFalse(each.isAvailable(),
                        "findAll must report the stored availability, not the default.");
            }
        }
    }

    @Test
    @DisplayName("T244: an identifier nobody holds returns an empty Optional")
    void unknownIdentifierFindsNothing() {
        repository.seedIfEmpty();

        assertTrue(repository.findById("DEP-99").isEmpty());
        assertTrue(repository.findById("").isEmpty());
        assertTrue(repository.findById(null).isEmpty());
    }

    @Test
    @DisplayName("A department's contact address is stored and read back")
    void contactAddressRoundTrips() {
        repository.seedIfEmpty();
        assertEquals("emergency@health.gov.au",
                repository.findById("DEP-02").orElseThrow().getContactEmail());
    }

    @Test
    @DisplayName("A null department is refused")
    void nullDepartmentRefused() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }
}
