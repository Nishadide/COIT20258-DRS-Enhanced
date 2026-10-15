package com.cqu.drs.server.handler;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Finds one disaster report by its reference.
 *
 * Public, because someone who reported a disaster and left no details still
 * has to be able to find out what is happening with it, and the reference is
 * the only thing they hold. The case study requires this, and the welcome
 * screen offers it without a sign-in.
 *
 * <p>Public does not mean unprotected. The reference is of the form DR-0001,
 * which is guessable, so this operation hands out whatever the reference
 * identifies. That is acceptable for the information a report carries and
 * would not be for anything more sensitive. The limitation is recorded rather
 * than papered over, and the remedy, which is a token issued to the reporter
 * at lodging time, is named in the report.
 */
public final class FindReportByReferenceHandler implements OperationHandler {

    private final com.cqu.drs.service.ReportOperations reportService;

    /**
     * Creates the handler.
     *
     * @param reportService the service that finds reports
     */
    public FindReportByReferenceHandler(com.cqu.drs.service.ReportOperations reportService) {
        if (reportService == null) {
            throw new IllegalArgumentException("A handler needs a report service.");
        }
        this.reportService = reportService;
    }

    @Override
    public Operation operation() {
        return Operation.REPORT_FIND_BY_REFERENCE;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        JsonElement element = request.getPayload().get("reference");
        if (element == null || element.isJsonNull() || element.getAsString().isBlank()) {
            throw ProtocolException.validation("A reference number must be given.");
        }

        // Upper-cased because references are stored as DR-0001 and a person
        // typing theirs in is as likely to use lower case. The screen did this
        // before the split; doing it here means any client gets the same
        // behaviour.
        String reference = element.getAsString().trim().toUpperCase();

        Optional<DisasterReport> found = reportService.findByReference(reference);
        if (found.isEmpty()) {
            throw ProtocolException.notFound("disaster report", reference);
        }

        JsonObject result = new JsonObject();
        // Serialised with the protocol's own Gson, which carries the adapters
        // for LocalDateTime, so the report arrives with its timestamps intact
        // and its nested assessment, tasks and alerts in place.
        result.add("report", ProtocolCodec.gson().toJsonTree(found.get()));
        return result;
    }
}
