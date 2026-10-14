package com.cqu.drs.service;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Reporter;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T17, T18 and T56 to T69: reference generation, the validation rules
 * and their boundaries, and lodging a report with or without a reporter.
 */
@DisplayName("ReportService")
class ReportServiceTest {

    private static final String ADDRESS = "12 Bridge Street, Melbourne";

    private ReportService reportService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        JsonDataStore store = new JsonDataStore(tempDir);
        reportService = new ReportService(
                new DisasterReportRepository(store),
                new AuditService(new AuditRepository(store)));
    }

    private List<String> validateWith(String address, String description, String contact) {
        return reportService.validate(DisasterType.FIRE, address, null, null,
                Severity.HIGH, description, contact);
    }

    @Test
    @DisplayName("T17: a generated reference has the form DR-nnnn")
    void assignReferenceMatchesFormat() {
        String reference = reportService.generateReference();
        assertTrue(reference.matches(DrsConstants.REFERENCE_PATTERN),
                "Reference " + reference + " does not match " + DrsConstants.REFERENCE_PATTERN);
    }

    @Test
    @DisplayName("T18: two reports do not share a reference")
    void assignReferenceIsUnique() {
        String first = reportService.submitReport(DisasterType.FIRE, ADDRESS, null, null,
                Severity.HIGH, null, null);
        String second = reportService.submitReport(DisasterType.FLOOD, ADDRESS, null, null,
                Severity.LOW, null, null);
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("T56: a complete set of details is accepted")
    void validateAcceptsValidReport() {
        assertTrue(validateWith(ADDRESS, "Smoke from the roof", "0412345678").isEmpty());
    }

    @Test
    @DisplayName("T57: a missing disaster type is reported")
    void validateRejectsMissingType() {
        List<String> errors = reportService.validate(null, ADDRESS, null, null,
                Severity.HIGH, null, null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).toLowerCase().contains("type"));
    }

    @Test
    @DisplayName("T58: a missing location is reported")
    void validateRejectsEmptyAddress() {
        List<String> errors = validateWith("", null, null);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).toLowerCase().contains("location"));
    }

    @Test
    @DisplayName("T59: a location of one character is accepted")
    void addressAcceptsLowerBound() {
        assertTrue(validateWith("A", null, null).isEmpty());
    }

    @Test
    @DisplayName("T60: a location of 200 characters is accepted")
    void addressAcceptsUpperBound() {
        assertTrue(validateWith("x".repeat(DrsConstants.ADDRESS_MAX_LENGTH),
                null, null).isEmpty());
    }

    @Test
    @DisplayName("T61: a location of 201 characters is refused")
    void addressRejectsAboveUpperBound() {
        assertFalse(validateWith("x".repeat(DrsConstants.ADDRESS_MAX_LENGTH + 1),
                null, null).isEmpty());
    }

    @Test
    @DisplayName("T62: a location of spaces alone is refused once trimmed")
    void validateRejectsWhitespaceAddress() {
        assertFalse(validateWith("     ", null, null).isEmpty());
    }

    @Test
    @DisplayName("T63: a description of 500 characters is accepted and 501 is refused")
    void descriptionLengthBoundary() {
        assertTrue(validateWith(ADDRESS,
                "x".repeat(DrsConstants.DESCRIPTION_MAX_LENGTH), null).isEmpty());
        assertFalse(validateWith(ADDRESS,
                "x".repeat(DrsConstants.DESCRIPTION_MAX_LENGTH + 1), null).isEmpty());
    }

    @Test
    @DisplayName("T64: contact numbers of 7 and 16 digits are refused, 8 and 15 accepted")
    void contactDigitsBoundary() {
        assertFalse(validateWith(ADDRESS, null, "1234567").isEmpty());
        assertTrue(validateWith(ADDRESS, null, "12345678").isEmpty());
        assertTrue(validateWith(ADDRESS, null, "123456789012345").isEmpty());
        assertFalse(validateWith(ADDRESS, null, "1234567890123456").isEmpty());
    }

    @Test
    @DisplayName("T65: two faults at once produce two messages, not one")
    void validateReportsAllFaults() {
        List<String> errors = reportService.validate(null, "", null, null,
                Severity.HIGH, null, null);
        assertEquals(2, errors.size(),
                "Validation must not stop at the first fault it finds.");
    }

    @Test
    @DisplayName("T66: a report from a registered reporter keeps the link")
    void submitLinksRegisteredReporter() {
        Reporter reporter = new Reporter("RP-01", "J. Perera", "0412345678");
        String reference = reportService.submitReport(DisasterType.EARTHQUAKE, ADDRESS,
                null, null, Severity.SEVERE, "Building shook", reporter);

        DisasterReport stored = reportService.findByReference(reference).orElseThrow();
        assertFalse(stored.isAnonymous());
        assertEquals("J. Perera", stored.getReporter().getName());
    }

    @Test
    @DisplayName("T67: an invalid report stores nothing")
    void submitStoresNothingWhenInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
                reportService.submitReport(null, "", null, null, null, null, null));
        assertTrue(reportService.findAll().isEmpty());
    }

    @Test
    @DisplayName("T68: a stored report is found by its reference")
    void findByReferenceReturnsReport() {
        String reference = reportService.submitReport(DisasterType.FIRE, ADDRESS,
                null, null, Severity.HIGH, null, null);
        assertTrue(reportService.findByReference(reference).isPresent());
    }

    @Test
    @DisplayName("T69: an unknown reference returns an empty Optional rather than null")
    void findByReferenceEmptyWhenAbsent() {
        Optional<DisasterReport> result = reportService.findByReference("DR-9999");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("T81: coordinates supplied by the device are kept")
    void captureStoresCoordinates() {
        String reference = reportService.submitReport(DisasterType.FLOOD, ADDRESS,
                -37.8136, 144.9631, Severity.HIGH, null, null);

        DisasterReport stored = reportService.findByReference(reference).orElseThrow();
        assertTrue(stored.getLocation().hasCoordinates());
    }

    @Test
    @DisplayName("T82: a report is still accepted when no coordinates are available")
    void captureDegradesToAddressOnly() {
        String reference = reportService.submitReport(DisasterType.FLOOD, ADDRESS,
                null, null, Severity.HIGH, null, null);

        DisasterReport stored = reportService.findByReference(reference).orElseThrow();
        assertFalse(stored.getLocation().hasCoordinates());
        assertEquals(ADDRESS, stored.getLocation().getAddress());
    }

    @Test
    @DisplayName("Coordinates outside the permitted range are refused")
    void coordinatesOutOfRangeRejected() {
        assertFalse(reportService.validate(DisasterType.FIRE, ADDRESS, 91.0, 0.0,
                Severity.HIGH, null, null).isEmpty());
    }
}
