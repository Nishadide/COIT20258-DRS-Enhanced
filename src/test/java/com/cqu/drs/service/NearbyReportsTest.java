package com.cqu.drs.service;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.Severity;
import com.cqu.drs.repository.AuditRepository;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.JsonDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T85 and T86: the proximity search that supports the geolocation
 * feature. When several people witness one disaster they lodge separate
 * reports, so a coordinator needs to see which reports describe the same
 * event.
 *
 * The coordinates used are around Melbourne. A tenth of a degree of latitude
 * is roughly eleven kilometres, which makes the separations predictable.
 */
@DisplayName("Nearby reports")
class NearbyReportsTest {

    /** The point every search in these tests measures from. */
    private static final Location ORIGIN =
            new Location("Federation Square", -37.8180, 144.9691);

    private ReportService reportService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        JsonDataStore store = new JsonDataStore(tempDir);
        reportService = new ReportService(
                new DisasterReportRepository(store),
                new AuditService(new AuditRepository(store)));
    }

    /**
     * Lodges a report at the coordinates given.
     *
     * @param address   the place name
     * @param latitude  degrees north
     * @param longitude degrees east
     * @return the reference given to the report
     */
    private String lodgeAt(String address, Double latitude, Double longitude) {
        return reportService.submitReport(DisasterType.FIRE, address,
                latitude, longitude, Severity.HIGH, null, null);
    }

    @Test
    @DisplayName("T85: only the reports inside the radius are returned")
    void nearbyReturnsOnlyWithinRadius() {
        lodgeAt("Flinders Street", -37.8183, 144.9671);   // a few hundred metres
        lodgeAt("Carlton Gardens", -37.8050, 144.9710);   // about 1.5 km
        lodgeAt("Geelong", -38.1499, 144.3617);           // about 60 km

        List<DisasterReport> nearby = reportService.nearbyReports(ORIGIN, 5.0);

        assertEquals(2, nearby.size(),
                "The distant report should not be returned.");
    }

    @Test
    @DisplayName("T85: the nearest report is returned first")
    void nearbyIsOrderedByDistance() {
        lodgeAt("Carlton Gardens", -37.8050, 144.9710);   // farther
        String nearest = lodgeAt("Flinders Street", -37.8183, 144.9671);

        List<DisasterReport> nearby = reportService.nearbyReports(ORIGIN, 5.0);

        assertEquals(nearest, nearby.get(0).getReferenceNumber());
    }

    @Test
    @DisplayName("T86: no report within the radius gives an empty list, not null")
    void nearbyEmptyWhenNoneInRange() {
        lodgeAt("Geelong", -38.1499, 144.3617);

        List<DisasterReport> nearby = reportService.nearbyReports(ORIGIN, 5.0);

        assertNotNull(nearby);
        assertTrue(nearby.isEmpty());
    }

    @Test
    @DisplayName("A report given as an address alone cannot be measured and is left out")
    void reportsWithoutCoordinatesAreExcluded() {
        lodgeAt("Flinders Street", -37.8183, 144.9671);
        reportService.submitReport(DisasterType.FLOOD, "Somewhere in the city",
                null, null, Severity.LOW, null, null);

        assertEquals(1, reportService.nearbyReports(ORIGIN, 5.0).size());
    }

    @Test
    @DisplayName("Searching around a report leaves that report out of its own results")
    void searchAroundReportExcludesItself() {
        String centre = lodgeAt("Flinders Street", -37.8183, 144.9671);
        lodgeAt("Carlton Gardens", -37.8050, 144.9710);

        List<DisasterReport> nearby = reportService.nearbyReports(centre);

        assertEquals(1, nearby.size());
        assertTrue(nearby.stream()
                .noneMatch(r -> centre.equals(r.getReferenceNumber())));
    }

    @Test
    @DisplayName("Searching around a report that has no coordinates returns nothing")
    void searchAroundReportWithoutCoordinates() {
        String centre = reportService.submitReport(DisasterType.FLOOD,
                "Somewhere in the city", null, null, Severity.LOW, null, null);

        assertTrue(reportService.nearbyReports(centre).isEmpty());
    }

    @Test
    @DisplayName("Searching around an unknown reference returns nothing")
    void searchAroundUnknownReference() {
        assertTrue(reportService.nearbyReports("DR-9999").isEmpty());
    }

    @Test
    @DisplayName("A search needs a point with coordinates")
    void searchRequiresCoordinates() {
        Location addressOnly = new Location("Somewhere in the city");
        assertThrows(IllegalArgumentException.class,
                () -> reportService.nearbyReports(addressOnly, 5.0));
        assertThrows(IllegalArgumentException.class,
                () -> reportService.nearbyReports(null, 5.0));
    }

    @Test
    @DisplayName("A radius of zero or less is refused")
    void searchRequiresPositiveRadius() {
        assertThrows(IllegalArgumentException.class,
                () -> reportService.nearbyReports(ORIGIN, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> reportService.nearbyReports(ORIGIN, -1.0));
    }

    @Test
    @DisplayName("The default radius is the one the system is configured with")
    void defaultRadiusIsUsed() {
        assertEquals(5.0, DrsConstants.DEFAULT_NEARBY_RADIUS_KM);
    }
}
