package com.cqu.drs.service;

import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules a report must satisfy before it is accepted.
 *
 * Held here, on their own, because they are now needed in two places and must
 * not be written twice. The client checks them so the person is told about an
 * empty field immediately rather than after a round trip to the server, and
 * the server checks them again when the report arrives.
 *
 * <p>The second check is not redundant. A check made only on the client is a
 * convenience, never a control: any program that speaks the protocol can send
 * whatever it likes, and the server has no way to know whether a well-behaved
 * client was on the other end. The reason both checks call this one method is
 * so that the two can never disagree about what the rules are.
 *
 * <p>Every fault is collected rather than returning at the first, so someone
 * filling in a form is told everything that is wrong in one go.
 */
public final class ReportValidation {

    /** Not instantiable: this class holds one method. */
    private ReportValidation() {
    }

    /**
     * Checks the details of a report.
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
    public static List<String> check(DisasterType type, String address,
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
}
