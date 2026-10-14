package com.cqu.drs.model;

import java.util.Objects;

/**
 * The person who lodged a disaster report. A reporter is deliberately not a
 * subclass of User: reporting is open to the public and requires no account,
 * so a report may carry no reporter at all. Registration is optional and only
 * affects whether the reporter can list the reports they have lodged.
 */
public class Reporter {

    private String reporterId;
    private String name;
    private String contactNumber;
    private boolean registered;

    /**
     * Creates a registered reporter.
     *
     * @param reporterId    unique identifier
     * @param name          the reporter's name
     * @param contactNumber contact number, or null if not supplied
     */
    public Reporter(String reporterId, String name, String contactNumber) {
        this.reporterId = reporterId;
        setName(name);
        setContactNumber(contactNumber);
        this.registered = true;
    }

    /** No-argument constructor required by Gson. */
    protected Reporter() {
    }

    /**
     * Creates an anonymous reporter, holding no identifying details.
     *
     * @return a reporter for which isAnonymous returns true
     */
    public static Reporter anonymous() {
        Reporter r = new Reporter();
        r.reporterId = null;
        r.name = null;
        r.contactNumber = null;
        r.registered = false;
        return r;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getName() {
        return name;
    }

    public final void setName(String name) {
        this.name = (name == null || name.trim().isEmpty()) ? null : name.trim();
    }

    public String getContactNumber() {
        return contactNumber;
    }

    /**
     * Sets the contact number. A null or blank value means none was supplied,
     * which is permitted. A value that is present must be digits only and of a
     * plausible length.
     *
     * @param contactNumber the contact number, or null
     */
    public final void setContactNumber(String contactNumber) {
        if (contactNumber == null || contactNumber.trim().isEmpty()) {
            this.contactNumber = null;
            return;
        }
        String trimmed = contactNumber.trim();
        if (!trimmed.matches("\\d+")) {
            throw new IllegalArgumentException("Contact number must contain digits only.");
        }
        if (trimmed.length() < DrsConstants.CONTACT_MIN_DIGITS
                || trimmed.length() > DrsConstants.CONTACT_MAX_DIGITS) {
            throw new IllegalArgumentException("Contact number must be between "
                    + DrsConstants.CONTACT_MIN_DIGITS + " and "
                    + DrsConstants.CONTACT_MAX_DIGITS + " digits.");
        }
        this.contactNumber = trimmed;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    /**
     * Reports whether this reporter supplied any identifying detail.
     *
     * @return true when no name is held
     */
    public boolean isAnonymous() {
        return name == null;
    }

    @Override
    public String toString() {
        return isAnonymous() ? "Anonymous reporter" : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reporter)) {
            return false;
        }
        Reporter that = (Reporter) o;
        return registered == that.registered
                && Objects.equals(reporterId, that.reporterId)
                && Objects.equals(name, that.name)
                && Objects.equals(contactNumber, that.contactNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reporterId, name, contactNumber, registered);
    }
}
