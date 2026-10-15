package com.cqu.drs.protocol;

import com.cqu.drs.json.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.time.LocalDateTime;

/**
 * Turns messages into lines and lines back into messages.
 *
 * The protocol frames messages by newline: one JSON object per line. That
 * works because Gson's compact output contains no literal newline. A newline
 * inside a string value is written as the two characters backslash and n, so a
 * disaster description typed across several lines cannot split one message
 * into two. {@link #encodeRequest} asserts this rather than assuming it.
 *
 * <p>The same Gson instance serialises both envelopes and is configured once
 * here. It reuses {@link LocalDateTimeAdapter}, written for Assessment 2 and
 * already covered by tests, so the protocol's timestamps and the stored
 * timestamps are formatted by the same code and cannot disagree.
 */
public final class ProtocolCodec {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            // Nulls are omitted rather than written. An absent sessionToken or
            // errorCode is the normal case, and leaving them out keeps the
            // messages small and readable in a log.
            .create();

    /** Not instantiable: this class holds static methods only. */
    private ProtocolCodec() {
    }

    /**
     * The Gson instance the protocol uses, for a handler building a payload
     * from a domain object.
     *
     * @return the configured Gson
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * Writes a request as one line, without the terminating newline.
     *
     * @param request the request
     * @return the line to send
     */
    public static String encodeRequest(Request request) {
        return requireSingleLine(GSON.toJson(request));
    }

    /**
     * Writes a response as one line, without the terminating newline.
     *
     * @param response the response
     * @return the line to send
     */
    public static String encodeResponse(Response response) {
        return requireSingleLine(GSON.toJson(response));
    }

    /**
     * Reads a request from one received line.
     *
     * @param line the line read from the socket
     * @return the request
     * @throws ProtocolException with MALFORMED_REQUEST when the line is not
     *         valid JSON, or is valid JSON that is not an object, or lacks a
     *         field the envelope requires
     */
    public static Request decodeRequest(String line) {
        Request request;
        try {
            request = GSON.fromJson(line, Request.class);
        } catch (JsonSyntaxException e) {
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "The request was not valid JSON.", e);
        }
        if (request == null) {
            // Gson returns null for an empty or blank line rather than
            // throwing, so this is checked separately.
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "The request was empty.");
        }
        if (!request.isWellFormed()) {
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "A request must carry requestId, operation and sentAt.");
        }
        return request;
    }

    /**
     * Reads a response from one received line.
     *
     * @param line the line read from the socket
     * @return the response
     * @throws ProtocolException with MALFORMED_REQUEST when the line cannot be
     *         read as a response
     */
    public static Response decodeResponse(String line) {
        try {
            Response response = GSON.fromJson(line, Response.class);
            if (response == null || response.getStatus() == null) {
                throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                        "The server sent a reply that was not a response.");
            }
            return response;
        } catch (JsonSyntaxException e) {
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "The server sent a reply that was not valid JSON.", e);
        }
    }

    /**
     * The compact JSON of a payload, used when signing and when verifying.
     *
     * Both sides must produce the same bytes for the same payload or every
     * signature would fail. They do, because a JsonObject keeps its fields in
     * the order they were added, and parsing adds them in the order they
     * appear in the message, so a payload serialised, sent, parsed and
     * serialised again is byte for byte what it started as.
     *
     * @param payload the payload, which may be null
     * @return the compact JSON, or an empty object when the payload is absent
     */
    public static String canonicalPayload(JsonObject payload) {
        return GSON.toJson(payload == null ? new JsonObject() : payload);
    }

    private static String requireSingleLine(String encoded) {
        if (encoded.indexOf('\n') >= 0 || encoded.indexOf('\r') >= 0) {
            // Unreachable with Gson's compact output. Checked anyway because
            // if it ever happened the failure would not look like a framing
            // fault: the receiver would read half a message, fail to parse it
            // and close the connection, and the real cause would be invisible.
            throw new IllegalStateException(
                    "A protocol message contained a line break, which would "
                            + "split it into two messages on the wire.");
        }
        return encoded;
    }
}
