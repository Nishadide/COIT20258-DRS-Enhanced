package com.cqu.drs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T14 to T16 and T64: anonymous reporting, and the contact number
 * boundaries.
 */
@DisplayName("Reporter")
class ReporterTest {

    @Test
    @DisplayName("T14: a reporter who gives no name is anonymous")
    void isAnonymousTrueWhenNoName() {
        Reporter reporter = Reporter.anonymous();
        assertTrue(reporter.isAnonymous());
        assertFalse(reporter.isRegistered());
        assertNull(reporter.getName());
    }

    @Test
    @DisplayName("T15: a registered reporter is not anonymous")
    void isAnonymousFalseWhenRegistered() {
        Reporter reporter = new Reporter("RP-01", "J. Perera", "0412345678");
        assertFalse(reporter.isAnonymous());
        assertTrue(reporter.isRegistered());
    }

    @Test
    @DisplayName("T16: toString shows the name and leaks no contact details")
    void toStringShowsNameOnly() {
        Reporter reporter = new Reporter("RP-01", "J. Perera", "0412345678");
        String text = reporter.toString();
        assertTrue(text.contains("J. Perera"));
        assertFalse(text.contains("0412345678"));
        assertFalse(text.contains("RP-01"));
    }

    @Test
    @DisplayName("An anonymous reporter describes itself as such")
    void anonymousToStringIsDescriptive() {
        assertEquals("Anonymous reporter", Reporter.anonymous().toString());
    }

    @Test
    @DisplayName("T64: a contact number of 7 digits is refused")
    void contactRejectsSevenDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> new Reporter("RP-02", "A. Singh", "1234567"));
    }

    @Test
    @DisplayName("T64: a contact number of 8 digits is accepted")
    void contactAcceptsEightDigits() {
        assertDoesNotThrow(() -> new Reporter("RP-03", "A. Singh", "12345678"));
    }

    @Test
    @DisplayName("T64: a contact number of 15 digits is accepted")
    void contactAcceptsFifteenDigits() {
        assertDoesNotThrow(() -> new Reporter("RP-04", "A. Singh", "123456789012345"));
    }

    @Test
    @DisplayName("T64: a contact number of 16 digits is refused")
    void contactRejectsSixteenDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> new Reporter("RP-05", "A. Singh", "1234567890123456"));
    }

    @Test
    @DisplayName("A contact number containing letters is refused")
    void contactRejectsNonDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> new Reporter("RP-06", "A. Singh", "04123abc78"));
    }

    @Test
    @DisplayName("No contact number is permitted")
    void contactMayBeAbsent() {
        Reporter reporter = new Reporter("RP-07", "A. Singh", null);
        assertNull(reporter.getContactNumber());
    }
}
