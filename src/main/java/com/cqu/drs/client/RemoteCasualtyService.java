package com.cqu.drs.client;

import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.service.CasualtyOperations;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The casualty registry, reached through the server.
 *
 * <h2>The client holds no key and reads no ciphertext</h2>
 * Encryption happens in the data tier, so what travels to this class is the
 * plain record and what sits in the database is the encrypted one. That is the
 * right division: the client is the machine most likely to be lost or stolen,
 * and giving it the key would mean a stolen laptop could read the registry
 * directly.
 *
 * <p>It does mean the values cross the socket readable, because the transport
 * is not TLS. That limitation is recorded in the report with its remedy rather
 * than being papered over by encrypting one field in the payload, which would
 * protect a name while leaving the sign-in password in the clear and would
 * give a false impression that the problem had been dealt with.
 */
public final class RemoteCasualtyService implements CasualtyOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteCasualtyService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    @Override
    public Casualty register(String reference, String name, Integer age,
                             String contactNumber, TriageCategory triage,
                             String location, String notes, String registeredBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);
        payload.addProperty("triage", triage == null ? null : triage.name());
        if (name != null && !name.isBlank()) {
            payload.addProperty("name", name);
        }
        if (age != null) {
            payload.addProperty("age", age);
        }
        if (contactNumber != null && !contactNumber.isBlank()) {
            payload.addProperty("contactNumber", contactNumber);
        }
        if (location != null && !location.isBlank()) {
            payload.addProperty("location", location);
        }
        if (notes != null && !notes.isBlank()) {
            payload.addProperty("notes", notes);
        }

        JsonObject result = client.sendOrThrow(Operation.CASUALTY_REGISTER, payload);
        return Results.one(result, "casualty", Casualty.class);
    }

    @Override
    public Casualty retriage(String casualtyId, TriageCategory triage, String changedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("casualtyId", casualtyId);
        payload.addProperty("triage", triage == null ? null : triage.name());

        JsonObject result = client.sendOrThrow(Operation.CASUALTY_TRIAGE, payload);
        return Results.one(result, "casualty", Casualty.class);
    }

    @Override
    public Casualty refer(String casualtyId, String hospital, String referredBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("casualtyId", casualtyId);
        payload.addProperty("hospital", hospital);

        JsonObject result = client.sendOrThrow(Operation.CASUALTY_REFER, payload);
        return Results.one(result, "casualty", Casualty.class);
    }

    /**
     * {@inheritDoc}
     *
     * A referral is not sent through here. It has its own operation because
     * it must carry the hospital with it, and a status set to REFERRED
     * without one would leave a record saying somebody was sent somewhere
     * unrecorded. The server refuses it as well; this check only saves the
     * round trip.
     */
    @Override
    public Casualty updateStatus(String casualtyId, CasualtyStatus status,
                                 String changedBy) {
        if (status == CasualtyStatus.REFERRED) {
            throw new IllegalArgumentException(
                    "Use the referral so that the hospital is recorded with it.");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("casualtyId", casualtyId);
        payload.addProperty("status", status == null ? null : status.name());

        JsonObject result = client.sendOrThrow(Operation.CASUALTY_STATUS, payload);
        return Results.one(result, "casualty", Casualty.class);
    }

    @Override
    public List<Casualty> findForReport(String reference) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        JsonObject result =
                client.sendOrThrow(Operation.CASUALTY_FIND_FOR_REPORT, payload);
        return Results.list(result, "casualties", Casualty.class);
    }

    @Override
    public Map<TriageCategory, Integer> summaryFor(String reference) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        JsonObject result = client.sendOrThrow(Operation.CASUALTY_SUMMARY, payload);
        JsonElement counts = result.get("counts");

        Map<TriageCategory, Integer> summary = new EnumMap<>(TriageCategory.class);
        for (TriageCategory category : TriageCategory.values()) {
            int value = 0;
            if (counts != null && counts.isJsonObject()
                    && counts.getAsJsonObject().has(category.name())) {
                value = counts.getAsJsonObject().get(category.name()).getAsInt();
            }
            summary.put(category, value);
        }
        return summary;
    }
}
