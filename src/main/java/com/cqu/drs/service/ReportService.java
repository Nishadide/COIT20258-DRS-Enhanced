package com.cqu.drs.service;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Location;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import com.cqu.drs.repository.DisasterReportRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Accepts disaster reports from the public and makes them available for
 * assessment. Reporting requires no account, so nothing here depends on a
 * user being signed in.
 */
public class ReportService {

    private final DisasterReportRepository reportRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param reportRepository where reports are stored
     * @param auditService     used to record submissions
     */
    public ReportService(DisasterReportRepository reportRepository, AuditService auditService) {
        this.reportRepository = reportRepository;
        this.auditService = auditService;
    }

    /**
     * Produces the next reference number, in the form DR-0001. Generating it
     * needs to know how many reports are already held, which is why this sits
     * in the service rather than on the report itself.
     *
     * @return a reference that no stored report is using
     */
    public String generateReference() {
        int next = reportRepository.count() + 1;
        String reference = String.format("%s%04d", DrsConstants.REFERENCE_PREFIX, next);
        while (reportRepository.findByReference(reference).isPresent()) {
            next++;
            reference = String.format("%s%04d", DrsConstants.REFERENCE_PREFIX, next);
        }
        return reference;
    }

    /**
     * Checks the details of a report before it is accepted, returning one
     * message for each fault found rather than stopping at the first, so that
     * the reporter can correct everything in one pass.
     *
     * @param type             the kind of disaster, required
     * @param address          where it happened, required
     * @param latitude         optional latitude
     * @param longitude        optional longitude
     * @param reportedSeverity how serious it appears, required
     * @param description      optional further detail
     * @param contactNumber    optional contact number
     * @return the faults found, empty when the details are acceptable
     */
    public List<String> validate(DisasterType type, String address,
                                 Double latitude, Double longitude,
                                 Severity reportedSeverity, String description,
                                 String contactNumber) {
        List<String> errors = new ArrayList<>();

        if (type == null) {
            errors.add("A disaster type must be selected.");
        }

        String trimmedAddress = address == null ? "" : address.trim();
        if (trimmedAddress.isEmpty()) {
            errors.add("A location must be given.");
        } else if (trimmedAddress.length() > DrsConstants.ADDRESS_MAX_LENGTH) {
            errors.add("The location may be at most "
                    + DrsConstants.ADDRESS_MAX_LENGTH + " characters.");
        }

        if (latitude != null && (latitude < DrsConstants.MIN_LATITUDE
                || latitude > DrsConstants.MAX_LATITUDE)) {
            errors.add("Latitude must be between " + DrsConstants.MIN_LATITUDE
                    + " and " + DrsConstants.MAX_LATITUDE + ".");
        }

        if (longitude != null && (longitude < DrsConstants.MIN_LONGITUDE
                || longitude > DrsConstants.MAX_LONGITUDE)) {
            errors.add("Longitude must be between " + DrsConstants.MIN_LONGITUDE
                    + " and " + DrsConstants.MAX_LONGITUDE + ".");
        }

        if (reportedSeverity == null) {
            errors.add("A severity must be selected.");
        }

        if (description != null && description.length() > DrsConstants.DESCRIPTION_MAX_LENGTH) {
            errors.add("The description may be at most "
                    + DrsConstants.DESCRIPTION_MAX_LENGTH + " characters.");
        }

        if (contactNumber != null && !contactNumber.trim().isEmpty()) {
            String trimmedContact = contactNumber.trim();
            if (!trimmedContact.matches("\\d+")) {
                errors.add("A contact number may contain digits only.");
            } else if (trimmedContact.length() < DrsConstants.CONTACT_MIN_DIGITS
                    || trimmedContact.length() > DrsConstants.CONTACT_MAX_DIGITS) {
                errors.add("A contact number must be between "
                        + DrsConstants.CONTACT_MIN_DIGITS + " and "
                        + DrsConstants.CONTACT_MAX_DIGITS + " digits.");
            }
        }

        return errors;
    }

    /**
     * Accepts a report and stores it. The reporter may be null, which is how
     * an anonymous report is lodged.
     *
     * @param type             the kind of disaster
     * @param address          where it happened
     * @param latitude         optional latitude
     * @param longitude        optional longitude
     * @param reportedSeverity how serious it appears
     * @param description      optional further detail
     * @param reporter         the reporter, or null when anonymous
     * @return the reference number given to the report
     * @throws IllegalArgumentException when the details are not acceptable
     */
    public String submitReport(DisasterType type, String address,
                               Double latitude, Double longitude,
                               Severity reportedSeverity, String description,
                               Reporter reporter) {
        List<String> errors = validate(type, address, latitude, longitude,
                reportedSeverity, description,
                reporter == null ? null : reporter.getContactNumber());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }

        String reference = generateReference();
        Location location = new Location(address, latitude, longitude);
        DisasterReport report = new DisasterReport(reference, type, location,
                reportedSeverity, description, reporter);
        reportRepository.save(report);

        auditService.record(reporter == null ? null : reporter.getName(),
                "Lodged report " + reference + " (" + type.getLabel() + ")");
        return reference;
    }

    /**
     * Finds a report by its reference number, which is how a reporter who left
     * no details checks on what they reported.
     *
     * @param reference the reference to look for
     * @return the report, or an empty Optional when none matches
     */
    public Optional<DisasterReport> findByReference(String reference) {
        return reportRepository.findByReference(reference);
    }

    /**
     * Every report held.
     *
     * @return the reports
     */
    public List<DisasterReport> findAll() {
        return reportRepository.findAll();
    }

    /**
     * Finds the reports lodged within a given distance of a point, nearest
     * first. Only reports whose location carries coordinates can be measured,
     * so a report given as an address alone is not returned.
     *
     * This supports the coordinator in seeing whether several reports describe
     * the same event, which is common when many people witness one disaster.
     *
     * @param from     the point to measure from
     * @param radiusKm how far to search, in kilometres
     * @return the reports within the radius, nearest first
     * @throws IllegalArgumentException if the point has no coordinates or the
     *                                  radius is not positive
     */
    public List<DisasterReport> nearbyReports(Location from, double radiusKm) {
        if (from == null || !from.hasCoordinates()) {
            throw new IllegalArgumentException(
                    "A point with coordinates is needed to search for nearby reports.");
        }
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("The radius must be greater than zero.");
        }

        List<DisasterReport> nearby = new ArrayList<>();
        for (DisasterReport report : reportRepository.findAll()) {
            Location location = report.getLocation();
            if (location != null && location.hasCoordinates()
                    && from.distanceTo(location) <= radiusKm) {
                nearby.add(report);
            }
        }
        nearby.sort(Comparator.comparingDouble(r -> from.distanceTo(r.getLocation())));
        return nearby;
    }

    /**
     * Finds the reports lodged near another report, excluding that report
     * itself, using the default search radius.
     *
     * @param reference the report to search around
     * @return the neighbouring reports, nearest first
     */
    public List<DisasterReport> nearbyReports(String reference) {
        Optional<DisasterReport> found = findByReference(reference);
        if (found.isEmpty() || !found.get().getLocation().hasCoordinates()) {
            return new ArrayList<>();
        }
        List<DisasterReport> nearby = nearbyReports(
                found.get().getLocation(), DrsConstants.DEFAULT_NEARBY_RADIUS_KM);
        nearby.removeIf(r -> reference.equals(r.getReferenceNumber()));
        return nearby;
    }
}
