package com.cqu.drs.protocol;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;

/**
 * One response travelling from server to client, as defined in section 4 of
 * PROTOCOL.md.
 *
 * A response is returned for every request, including one the server refuses,
 * so that a client never has to tell a refusal from a dropped connection by
 * waiting for a timeout.
 *
 * <p>Both server timestamps are carried. {@code receivedAt} is taken when the
 * request was read and {@code respondedAt} when the reply was written, which
 * together with the client's own {@code sentAt} give the three timestamps the
 * time stamping requirement asks for.
 */
public class Response {

    private String requestId;
    private ResponseStatus status;
    private LocalDateTime receivedAt;
    private LocalDateTime respondedAt;
    private JsonObject payload;
    private String errorCode;
    private String message;

    /** No-argument constructor required by Gson. */
    protected Response() {
    }

    private Response(String requestId, ResponseStatus status, LocalDateTime receivedAt,
                     JsonObject payload, ErrorCode errorCode, String message) {
        this.requestId = requestId;
        this.status = status;
        this.receivedAt = receivedAt;
        this.payload = payload;
        this.errorCode = errorCode == null ? null : errorCode.name();
        this.message = message;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * A successful response.
     *
     * @param requestId  the identifier of the request being answered
     * @param receivedAt when the server read the request
     * @param payload    the result, or an empty object for an operation that
     *                   returns nothing
     * @return the response
     */
    public static Response ok(String requestId, LocalDateTime receivedAt, JsonObject payload) {
        return new Response(requestId, ResponseStatus.OK, receivedAt,
                payload == null ? new JsonObject() : payload, null, null);
    }

    /**
     * A refusal.
     *
     * @param requestId  the identifier of the request being refused, which may
     *                   be null when the request was too malformed to carry one
     * @param receivedAt when the server read the request
     * @param errorCode  why the request was refused
     * @param message    text suitable for showing to the person at the keyboard
     * @return the response
     */
    public static Response error(String requestId, LocalDateTime receivedAt,
                                 ErrorCode errorCode, String message) {
        return new Response(requestId, ResponseStatus.ERROR, receivedAt, null,
                errorCode, message);
    }

    public String getRequestId() {
        return requestId;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Whether the operation ran.
     *
     * @return true when the status is OK
     */
    public boolean isOk() {
        return status == ResponseStatus.OK;
    }

    /**
     * The error code as an enum, for a client deciding what to do next.
     *
     * @return the code, or null when the response succeeded or carried a code
     *         this version of the client does not recognise
     */
    public ErrorCode errorCodeOrNull() {
        if (errorCode == null) {
            return null;
        }
        try {
            return ErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            // A server newer than this client may send a code that did not
            // exist when the client was built. Returning null lets the client
            // fall back to showing the message rather than failing outright.
            return null;
        }
    }

    @Override
    public String toString() {
        return status + (errorCode == null ? "" : " " + errorCode)
                + " [" + requestId + "]";
    }
}
