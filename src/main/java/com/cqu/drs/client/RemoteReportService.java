package com.cqu.drs.client;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.ReportOperations;
import com.cqu.drs.service.ReportValidation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Answers the screens' questions about reports by asking the server.
 *
 * This is the client half of the three-tier split. It implements the same
 * interface as {@link com.cqu.drs.service.ReportService}, so
 * {@code ReportDisasterController} and {@code TrackReportController} call the
 * same methods they always did and are unaware that a socket is involved.
 *
 * <p>It holds no repository, no database connection and no business rules
 * beyond the validation it performs for immediate feedback. Everything that
 * decides anything happens on the server, which is what makes the client
 * replaceable and the rules enforceable.
 */
public final class RemoteReportService implements ReportOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteReportService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    /**
     * {@inheritDoc}
     *
     * Answered without contacting the server. The rules need nothing but the
     * values on the screen, and making someone wait for a round trip to be
     * told a field is empty would make the form feel broken. The server
     * applies the same rules, through the same class, when the report arrives.
     */
    @Override
    public List<String> validate(DisasterType type, String address,
                                 Double latitude, Double longitude,
                                 Severity reportedSeverity, String description,
                                 String contactNumber) {
        return ReportValidation.check(type, address, latitude, longitude,
                reportedSeverity, description, contactNumber);
    }

    /**
     * {@inheritDoc}
     *
     * The reporter's name and contact number are sent, not the Reporter object
     * the screen built. Its identifier was generated on the client, and
     * identifiers are allocated by the tier that stores them. The server makes
     * the reporter record and decides what it is called.
     */
    @Override
    public String submitReport(DisasterType type, String address,
                               Double latitude, Double longitude,
                               Severity reportedSeverity, String description,
                               Reporter reporter) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type == null ? null : type.name());
        payload.addProperty("address", address);
        if (latitude != null) {
            payload.addProperty("latitude", latitude);
        }
        if (longitude != null) {
            payload.addProperty("longitude", longitude);
        }
        payload.addProperty("severity",
                reportedSeverity == null ? null : reportedSeverity.name());
        payload.addProperty("description", description);
        if (reporter != null && !reporter.isAnonymous()) {
            payload.addProperty("reporterName", reporter.getName());
            payload.addProperty("contactNumber", reporter.getContactNumber());
        }

        // A rule the server rejected reaches the screen as the same kind of
        // failure the local service raises, because the client translates
        // every refusal in one place.
        return client.sendOrThrow(Operation.REPORT_SUBMIT, payload)
                .get("referenceNumber").getAsString();
    }

    @Override
    public Optional<DisasterReport> findByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        // Not found is an ordinary answer here, not an error: someone
        // mistyping their reference is a thing people do, and the screen
        // already knows how to say so.
        return client.sendAllowingNotFound(Operation.REPORT_FIND_BY_REFERENCE, payload)
                .map(result -> readReport(result.get("report")));
    }

    @Override
    public List<DisasterReport> findAll() {
        JsonObject result = client.sendOrThrow(Operation.REPORT_FIND_ALL, new JsonObject());
        return readReports(result.get("reports"));
    }

    @Override
    public List<DisasterReport> nearbyReports(String reference) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        JsonObject result = client.sendOrThrow(Operation.REPORT_NEARBY, payload);
        return readReports(result.get("reports"));
    }

    private static DisasterReport readReport(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return ProtocolCodec.gson().fromJson(element, DisasterReport.class);
    }

    private static List<DisasterReport> readReports(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }
        JsonArray array = element.getAsJsonArray();
        List<DisasterReport> reports = new ArrayList<>(array.size());
        for (JsonElement each : array) {
            DisasterReport report = readReport(each);
            if (report != null) {
                reports.add(report);
            }
        }
        return reports;
    }
}
