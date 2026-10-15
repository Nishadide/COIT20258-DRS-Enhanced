package com.cqu.drs.service;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;

import java.util.List;
import java.util.Optional;

/**
 * Everything the screens can ask about disaster reports.
 *
 * This interface exists because from Assessment 3 onwards there are two ways
 * to answer those questions, and the screens must not be able to tell which
 * one they are talking to.
 *
 * <ul>
 *   <li>{@link ReportService} answers them directly, by reaching the
 *       repository in the same process. This is how DRS-Initial worked and it
 *       is still how the server itself works, because on the server the
 *       repository is right there.</li>
 *   <li>{@code RemoteReportService} answers them by sending a request to the
 *       server and waiting for the reply. This is what the JavaFX client uses
 *       once the application is running in three tiers.</li>
 * </ul>
 *
 * <p>Both are referred to through this interface, so
 * {@code ReportDisasterController} calls {@code submitReport} exactly as it
 * did in Assessment 2 and has no idea whether a socket was involved. That is
 * the point of the exercise: the presentation tier is unchanged by the move to
 * a three-tier architecture, because it only ever spoke to an interface.
 *
 * <p>It also keeps MVC intact across the split. The controller still talks to
 * one thing that performs operations on the model. Which side of a network
 * connection that thing lives on is not a concern the controller has.
 */
public interface ReportOperations {

    /**
     * Checks the details of a report before it is accepted, returning one
     * message per fault rather than stopping at the first.
     *
     * This is answered on the client even when everything else travels to the
     * server, because it needs nothing but the values on the screen, and
     * waiting for a network round trip to be told a field is empty would make
     * the form feel broken. The server checks again when the report arrives:
     * a check made only on the client is a convenience, never a control.
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
    List<String> validate(DisasterType type, String address,
                          Double latitude, Double longitude,
                          Severity reportedSeverity, String description,
                          String contactNumber);

    /**
     * Lodges a report and returns the reference it was given.
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
    String submitReport(DisasterType type, String address,
                        Double latitude, Double longitude,
                        Severity reportedSeverity, String description,
                        Reporter reporter);

    /**
     * Finds a report by its reference, which is how someone who left no
     * details checks on what they reported.
     *
     * @param reference the reference to look for
     * @return the report, or an empty Optional when none matches
     */
    Optional<DisasterReport> findByReference(String reference);

    /**
     * Every report held.
     *
     * @return the reports
     */
    List<DisasterReport> findAll();

    /**
     * Other reports close to the one given, which is how a coordinator sees
     * that several calls describe one incident.
     *
     * @param reference the report to search around
     * @return the nearby reports, nearest first
     */
    List<DisasterReport> nearbyReports(String reference);
}
