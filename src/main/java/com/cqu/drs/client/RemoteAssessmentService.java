package com.cqu.drs.client;

import com.cqu.drs.model.Assessment;
import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.Priority;
import com.cqu.drs.model.Severity;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.service.AssessmentOperations;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * Records and reads assessments by asking the server.
 *
 * The coordinator's screens call the same methods they called in Assessment 2.
 * Nothing in them knows that a socket is involved, which is the point: the
 * presentation tier was written against an interface and so survived the move
 * to three tiers unchanged.
 */
public final class RemoteAssessmentService implements AssessmentOperations {

    private final DrsClient client;

    /**
     * Creates the service.
     *
     * @param client the connection to the server
     */
    public RemoteAssessmentService(DrsClient client) {
        if (client == null) {
            throw new IllegalArgumentException("A remote service needs a client.");
        }
        this.client = client;
    }

    /**
     * {@inheritDoc}
     *
     * The assessor is not sent. The server records the assessment against the
     * user on the session, so an assessment cannot be attributed to somebody
     * else by a client that says it belongs to them.
     */
    @Override
    public Assessment assess(String reference, Severity severity, String notes,
                             String assessedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);
        payload.addProperty("severity", severity == null ? null : severity.name());
        if (notes != null && !notes.isBlank()) {
            payload.addProperty("notes", notes);
        }

        JsonObject result = client.sendOrThrow(Operation.ASSESSMENT_ASSESS, payload);
        return Results.one(result, "assessment", Assessment.class);
    }

    /**
     * {@inheritDoc}
     *
     * Answered on the client, from the assessment already in hand, because the
     * priority is derived from the severity by the assessment itself rather
     * than stored anywhere. Both tiers call the same method on the same model
     * class, so neither can arrive at a different answer.
     */
    @Override
    public Priority prioritise(Assessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("Cannot prioritise a null assessment.");
        }
        return assessment.derivePriority();
    }

    @Override
    public List<DisasterReport> queueByPriority() {
        JsonObject result = client.sendOrThrow(
                Operation.ASSESSMENT_QUEUE_BY_PRIORITY, new JsonObject());
        return Results.list(result, "reports", DisasterReport.class);
    }

    @Override
    public List<DisasterReport> awaitingAssessment() {
        JsonObject result = client.sendOrThrow(
                Operation.ASSESSMENT_AWAITING, new JsonObject());
        return Results.list(result, "reports", DisasterReport.class);
    }

    @Override
    public void resolve(String reference, String resolvedBy) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        client.sendOrThrow(Operation.ASSESSMENT_RESOLVE, payload);
    }

    /**
     * {@inheritDoc}
     *
     * Sent as the public lookup rather than a coordinator-only one, because
     * the operation that answers it is the same whichever screen is asking and
     * the server decides what each session may see.
     */
    @Override
    public Optional<DisasterReport> findByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", reference);

        return client.sendAllowingNotFound(Operation.REPORT_FIND_BY_REFERENCE, payload)
                .flatMap(result -> Results.optional(result, "report", DisasterReport.class));
    }

}
