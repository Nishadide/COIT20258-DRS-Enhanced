package com.cqu.drs.server.handler;

import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.ReportOperations;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.List;
import java.util.UUID;

/**
 * Lodges a disaster report and returns the reference it was given.
 *
 * Public, because the case study requires a member of the public to be able to
 * report a disaster without an account. It is the only public operation that
 * writes anything.
 *
 * <p>The reporter's record is created here rather than taken from the client.
 * The client sends a name and a contact number; the identifier is allocated by
 * the tier that stores it. An identifier chosen by a caller is an identifier
 * the caller can collide or reuse, by accident or deliberately.
 */
public final class SubmitReportHandler implements OperationHandler {

    private final ReportOperations reportService;

    /**
     * Creates the handler.
     *
     * @param reportService the service that lodges reports, which on the
     *                      server is the local one holding the repository
     */
    public SubmitReportHandler(ReportOperations reportService) {
        if (reportService == null) {
            throw new IllegalArgumentException("A handler needs a report service.");
        }
        this.reportService = reportService;
    }

    @Override
    public Operation operation() {
        return Operation.REPORT_SUBMIT;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        JsonObject payload = request.getPayload();

        DisasterType type = readEnum(payload, "type", DisasterType.class);
        Severity severity = readEnum(payload, "severity", Severity.class);
        String address = readString(payload, "address");
        String description = readString(payload, "description");
        Double latitude = readDouble(payload, "latitude");
        Double longitude = readDouble(payload, "longitude");
        String reporterName = readString(payload, "reporterName");
        String contactNumber = readString(payload, "contactNumber");

        // Checked here as well as on the client. The client's check is for the
        // person filling in the form; this one is the control, because any
        // program can send this request and the server cannot tell whether a
        // well-behaved client was at the other end.
        List<String> faults = reportService.validate(type, address, latitude, longitude,
                severity, description, contactNumber);
        if (!faults.isEmpty()) {
            throw ProtocolException.validation(String.join(" ", faults));
        }

        Reporter reporter = buildReporter(reporterName, contactNumber);

        try {
            String reference = reportService.submitReport(type, address, latitude,
                    longitude, severity, description, reporter);

            JsonObject result = new JsonObject();
            result.addProperty("referenceNumber", reference);
            return result;

        } catch (IllegalArgumentException e) {
            // A domain rule the validation above does not cover, raised from
            // inside the model.
            throw ProtocolException.validation(e.getMessage());

        } catch (IllegalStateException e) {
            // Raised when a free reference could not be reserved after several
            // attempts, which means sustained contention rather than a fault
            // in the request. The client may reasonably try again.
            throw new ProtocolException(ErrorCode.CONFLICT, e.getMessage(), e);
        }
    }

    private static Reporter buildReporter(String name, String contactNumber) {
        if (name == null || name.isBlank()) {
            // No name means the report is anonymous, which the case study
            // allows and which the schema permits with a null reporter.
            return null;
        }
        return new Reporter(DrsConstants.REPORTER_PREFIX
                + UUID.randomUUID().toString().substring(0, DrsConstants.SHORT_ID_LENGTH),
                name, contactNumber);
    }

    private static String readString(JsonObject payload, String field) {
        JsonElement element = payload.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String value = element.getAsString();
        return value.isBlank() ? null : value;
    }

    private static Double readDouble(JsonObject payload, String field) {
        JsonElement element = payload.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException | JsonSyntaxException | IllegalStateException e) {
            throw ProtocolException.validation(
                    field + " must be a number, but \"" + element + "\" was sent.");
        }
    }

    private static <E extends Enum<E>> E readEnum(JsonObject payload, String field,
                                                  Class<E> type) {
        String value = readString(payload, field);
        if (value == null) {
            // Left null so that validate reports it as a missing field, in the
            // same words the person would see filling in the form.
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(
                    "\"" + value + "\" is not a recognised " + field + ".");
        }
    }
}
