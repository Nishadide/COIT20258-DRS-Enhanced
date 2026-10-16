package com.cqu.drs.server.handler;

import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.cqu.drs.service.CasualtyOperations;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * How many casualties fall into each triage category.
 *
 * <h2>Counts, and nothing that could identify anybody</h2>
 * This is the one casualty operation an administrator may invoke, and the
 * reason it is safe to let them is visible in what it returns: four numbers.
 * It is built from a COUNT in the database rather than by loading the records
 * and counting them, so producing the summary never decrypts a name.
 *
 * <p>Every category is present even when its count is zero, because a summary
 * that omitted "Immediate: 0" reads as the figure not having been collected
 * rather than as there being none, and those are very different things to the
 * person reading it.
 */
public final class CasualtySummaryHandler implements OperationHandler {

    private final CasualtyOperations service;

    /**
     * Creates the handler.
     *
     * @param service the service this handler works through
     */
    public CasualtySummaryHandler(CasualtyOperations service) {
        if (service == null) {
            throw new IllegalArgumentException("A handler needs its collaborators.");
        }
        this.service = service;
    }

    @Override
    public Operation operation() {
        return Operation.CASUALTY_SUMMARY;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        String reference = Payloads.requireString(request.getPayload(), "reference");

        try {
            Map<TriageCategory, Integer> counts = service.summaryFor(reference);

            JsonObject summary = new JsonObject();
            int total = 0;
            for (Map.Entry<TriageCategory, Integer> entry : counts.entrySet()) {
                summary.addProperty(entry.getKey().name(), entry.getValue());
                total += entry.getValue();
            }

            JsonObject result = new JsonObject();
            result.add("counts", summary);
            result.addProperty("total", total);
            return result;

        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(e.getMessage());
        }
    }
}
