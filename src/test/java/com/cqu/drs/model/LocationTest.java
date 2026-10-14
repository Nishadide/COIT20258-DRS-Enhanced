package com.cqu.drs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T05 to T13: optional coordinates, the boundaries of the latitude and
 * longitude ranges, and the distance calculation used by the geolocation
 * feature.
 */
@DisplayName("Location")
class LocationTest {

    private static final String ADDRESS = "12 Bridge Street, Melbourne";

    @Test
    @DisplayName("T05: both coordinates present means the location is mappable")
    void hasCoordinatesTrueWhenBothSet() {
        Location location = new Location(ADDRESS, -37.8136, 144.9631);
        assertTrue(location.hasCoordinates());
    }

    @Test
    @DisplayName("T06: no coordinates at all")
    void hasCoordinatesFalseWhenUnset() {
        Location location = new Location(ADDRESS);
        assertFalse(location.hasCoordinates());
    }

    @Test
    @DisplayName("T07: one coordinate alone cannot place anything on a map")
    void hasCoordinatesFalseWhenPartial() {
        Location location = new Location(ADDRESS, -37.8136, null);
        assertFalse(location.hasCoordinates());
    }

    @Test
    @DisplayName("T08: latitude accepts the lower bound of -90")
    void latitudeAcceptsLowerBound() {
        assertDoesNotThrow(() -> new Location(ADDRESS, -90.0, 0.0));
    }

    @Test
    @DisplayName("T09: latitude accepts the upper bound of 90")
    void latitudeAcceptsUpperBound() {
        assertDoesNotThrow(() -> new Location(ADDRESS, 90.0, 0.0));
    }

    @Test
    @DisplayName("T10: latitude rejects a value just below the lower bound")
    void latitudeRejectsBelowLowerBound() {
        assertThrows(IllegalArgumentException.class,
                () -> new Location(ADDRESS, -90.1, 0.0));
    }

    @Test
    @DisplayName("T11: latitude rejects a value just above the upper bound")
    void latitudeRejectsAboveUpperBound() {
        assertThrows(IllegalArgumentException.class,
                () -> new Location(ADDRESS, 90.1, 0.0));
    }

    @Test
    @DisplayName("T12: longitude accepts both bounds")
    void longitudeAcceptsBothBounds() {
        assertDoesNotThrow(() -> new Location(ADDRESS, 0.0, -180.0));
        assertDoesNotThrow(() -> new Location(ADDRESS, 0.0, 180.0));
    }

    @Test
    @DisplayName("T13: longitude rejects a value just above the upper bound")
    void longitudeRejectsAboveUpperBound() {
        assertThrows(IllegalArgumentException.class,
                () -> new Location(ADDRESS, 0.0, 180.1));
    }

    @Test
    @DisplayName("T91: longitude rejects a value just below the lower bound")
    void longitudeRejectsBelowLowerBound() {
        assertThrows(IllegalArgumentException.class,
                () -> new Location(ADDRESS, 0.0, -180.1));
    }

    @Test
    @DisplayName("T83: distance between two known points is about 5 km")
    void distanceMatchesKnownSeparation() {
        // Two points separated by 0.0449 degrees of latitude, which is close to 5 km.
        Location from = new Location("Point A", -37.8136, 144.9631);
        Location to = new Location("Point B", -37.7687, 144.9631);
        assertEquals(5.0, from.distanceTo(to), 0.1);
    }

    @Test
    @DisplayName("T84: the distance from a point to itself is zero")
    void distanceZeroForSamePoint() {
        Location point = new Location(ADDRESS, -37.8136, 144.9631);
        assertEquals(0.0, point.distanceTo(point), 0.0001);
    }

    @Test
    @DisplayName("A distance cannot be measured without coordinates")
    void distanceRefusedWithoutCoordinates() {
        Location withCoords = new Location(ADDRESS, -37.8136, 144.9631);
        Location withoutCoords = new Location(ADDRESS);
        assertThrows(IllegalStateException.class,
                () -> withCoords.distanceTo(withoutCoords));
    }

    @Test
    @DisplayName("An empty address is refused")
    void addressRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new Location("   "));
    }
}
