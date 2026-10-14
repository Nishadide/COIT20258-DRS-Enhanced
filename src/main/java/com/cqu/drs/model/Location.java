package com.cqu.drs.model;

import java.util.Objects;

/**
 * Where a disaster occurred. An address is always required; coordinates are
 * optional, because a reporter may not be able to supply them. The coordinate
 * fields are nullable Doubles rather than primitives so that "not supplied"
 * can be distinguished from the valid position zero, zero.
 */
public class Location {

    private String address;
    private Double latitude;
    private Double longitude;

    /**
     * Creates a location with an address only.
     *
     * @param address the street address or description of the place
     */
    public Location(String address) {
        setAddress(address);
        this.latitude = null;
        this.longitude = null;
    }

    /**
     * Creates a location with an address and coordinates.
     *
     * @param address   the street address or description of the place
     * @param latitude  degrees north, between -90 and 90, or null
     * @param longitude degrees east, between -180 and 180, or null
     */
    public Location(String address, Double latitude, Double longitude) {
        setAddress(address);
        setLatitude(latitude);
        setLongitude(longitude);
    }

    /** No-argument constructor required by Gson. */
    protected Location() {
    }

    public String getAddress() {
        return address;
    }

    public final void setAddress(String address) {
        String trimmed = address == null ? "" : address.trim();
        if (trimmed.length() < DrsConstants.ADDRESS_MIN_LENGTH
                || trimmed.length() > DrsConstants.ADDRESS_MAX_LENGTH) {
            throw new IllegalArgumentException("Address must be between "
                    + DrsConstants.ADDRESS_MIN_LENGTH + " and "
                    + DrsConstants.ADDRESS_MAX_LENGTH + " characters.");
        }
        this.address = trimmed;
    }

    public Double getLatitude() {
        return latitude;
    }

    public final void setLatitude(Double latitude) {
        if (latitude != null && (latitude < DrsConstants.MIN_LATITUDE
                || latitude > DrsConstants.MAX_LATITUDE)) {
            throw new IllegalArgumentException("Latitude must be between "
                    + DrsConstants.MIN_LATITUDE + " and " + DrsConstants.MAX_LATITUDE + ".");
        }
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public final void setLongitude(Double longitude) {
        if (longitude != null && (longitude < DrsConstants.MIN_LONGITUDE
                || longitude > DrsConstants.MAX_LONGITUDE)) {
            throw new IllegalArgumentException("Longitude must be between "
                    + DrsConstants.MIN_LONGITUDE + " and " + DrsConstants.MAX_LONGITUDE + ".");
        }
        this.longitude = longitude;
    }

    /**
     * Reports whether both coordinates are present. A location with only one
     * of the two is treated as having none, because a single coordinate cannot
     * place anything on a map.
     *
     * @return true when both latitude and longitude are set
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * Great-circle distance from this location to another, using the haversine
     * formula.
     *
     * @param other the location to measure to
     * @return the distance in kilometres
     * @throws IllegalStateException if either location lacks coordinates
     */
    public double distanceTo(Location other) {
        if (other == null || !this.hasCoordinates() || !other.hasCoordinates()) {
            throw new IllegalStateException(
                    "Both locations must have coordinates to measure a distance.");
        }
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return DrsConstants.EARTH_RADIUS_KM * c;
    }

    @Override
    public String toString() {
        if (hasCoordinates()) {
            return String.format("%s (%.4f, %.4f)", address, latitude, longitude);
        }
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Location)) {
            return false;
        }
        Location that = (Location) o;
        return Objects.equals(address, that.address)
                && Objects.equals(latitude, that.latitude)
                && Objects.equals(longitude, that.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, latitude, longitude);
    }
}
