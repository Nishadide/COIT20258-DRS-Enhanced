package com.cqu.drs.protocol;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One request travelling from client to server, as defined in section 3 of
 * PROTOCOL.md.
 *
 * The payload is held as a {@link JsonObject} rather than a typed class
 * because each operation carries a different payload. Keeping it untyped at
 * this level means the envelope is parsed once, by one piece of code, and the
 * handler for the operation reads only the fields it needs.
 */
public class Request {

    private String requestId;
    private String operation;
    private String sessionToken;
    private LocalDateTime sentAt;
    private JsonObject payload;
    private String signature;

    /**
     * Creates a request with a fresh identifier stamped with the client's
     * clock.
     *
     * @param operation the wire name of the operation
     * @param payload   the operation's arguments, or an empty object
     */
    public Request(String operation, JsonObject payload) {
        this.requestId = UUID.randomUUID().toString();
        this.operation = operation;
        this.payload = payload == null ? new JsonObject() : payload;
        this.sentAt = LocalDateTime.now();
    }

    /** No-argument constructor required by Gson. */
    protected Request() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    /**
     * The operation's arguments.
     *
     * Never null for a request built by this class, so a handler can read a
     * field without first checking the payload exists.
     *
     * @return the payload
     */
    public JsonObject getPayload() {
        if (payload == null) {
            payload = new JsonObject();
        }
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * Whether this request carries the fields the envelope requires.
     *
     * Checked before anything else is attempted, because a request missing its
     * identifier or its operation cannot be answered, logged or refused
     * meaningfully.
     *
     * @return true when the envelope is complete enough to process
     */
    public boolean isWellFormed() {
        return isPresent(requestId) && isPresent(operation) && sentAt != null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * A description for the server log that names the operation and the
     * request but never the payload, which may hold a password.
     *
     * @return a one line description
     */
    @Override
    public String toString() {
        return operation + " [" + requestId + "]";
    }
}
