package com.cqu.drs.protocol;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T122 to T131: turning messages into lines and back.
 *
 * The framing tests matter more than they look. The protocol separates
 * messages by newline, so anything that could put a newline inside a message
 * would split one message into two and leave the connection reading halves of
 * messages from then on. That failure does not look like a framing fault when
 * it happens, so it is tested for directly.
 */
@DisplayName("ProtocolCodec")
class ProtocolCodecTest {

    @Test
    @DisplayName("T122: a request survives being encoded and decoded")
    void requestRoundTrips() {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", "DR-0001");

        Request original = new Request(Operation.REPORT_FIND_BY_REFERENCE.wireName(), payload);
        original.setSessionToken("token-abc");
        original.setSignature("deadbeef");

        Request decoded = ProtocolCodec.decodeRequest(ProtocolCodec.encodeRequest(original));

        assertEquals(original.getRequestId(), decoded.getRequestId());
        assertEquals("report.findByReference", decoded.getOperation());
        assertEquals("token-abc", decoded.getSessionToken());
        assertEquals("deadbeef", decoded.getSignature());
        assertEquals("DR-0001", decoded.getPayload().get("reference").getAsString());
    }

    @Test
    @DisplayName("T123: a timestamp keeps its microseconds across the wire")
    void timestampKeepsMicroseconds() {
        // The audit trail orders actions that can happen in the same second,
        // so losing sub-second precision in transit would make two actions
        // appear simultaneous.
        LocalDateTime precise = LocalDateTime.of(2026, 10, 15, 9, 14, 22, 481_365_000);

        Request request = new Request(Operation.SERVER_PING.wireName(), new JsonObject());
        request.setSentAt(precise);

        Request decoded = ProtocolCodec.decodeRequest(ProtocolCodec.encodeRequest(request));

        assertEquals(precise, decoded.getSentAt());
    }

    @Test
    @DisplayName("T124: an encoded message never contains a line break")
    void encodedMessageIsOneLine() {
        // A disaster description typed across several lines is the realistic
        // way this would happen.
        JsonObject payload = new JsonObject();
        payload.addProperty("description",
                "Fire on the first floor.\nSecond floor is clear.\r\nRoad is blocked.");

        String line = ProtocolCodec.encodeRequest(
                new Request(Operation.REPORT_SUBMIT.wireName(), payload));

        assertFalse(line.contains("\n"), "A newline would split this into two messages.");
        assertFalse(line.contains("\r"), "A carriage return would split this into two messages.");
    }

    @Test
    @DisplayName("T125: a multi-line value is returned exactly as it was sent")
    void multiLineValueSurvives() {
        String description = "Fire on the first floor.\nSecond floor is clear.";
        JsonObject payload = new JsonObject();
        payload.addProperty("description", description);

        Request decoded = ProtocolCodec.decodeRequest(ProtocolCodec.encodeRequest(
                new Request(Operation.REPORT_SUBMIT.wireName(), payload)));

        assertEquals(description, decoded.getPayload().get("description").getAsString());
    }

    @Test
    @DisplayName("T126: a line that is not valid JSON is refused as malformed")
    void invalidJsonRefused() {
        ProtocolException refused = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest("{this is not json"));
        assertEquals(ErrorCode.MALFORMED_REQUEST, refused.getErrorCode());
    }

    @Test
    @DisplayName("T127: an empty line is refused rather than read as a null request")
    void emptyLineRefused() {
        // Gson returns null for an empty document rather than throwing, so
        // without a check of its own this would reach the dispatcher as a null
        // and fail there with a NullPointerException.
        ProtocolException refused = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest(""));
        assertEquals(ErrorCode.MALFORMED_REQUEST, refused.getErrorCode());
    }

    @Test
    @DisplayName("T128: a request with no operation is refused as malformed")
    void missingOperationRefused() {
        String line = "{\"requestId\":\"abc\",\"sentAt\":\"2026-10-15T09:14:22\"}";

        ProtocolException refused = assertThrows(ProtocolException.class,
                () -> ProtocolCodec.decodeRequest(line));
        assertEquals(ErrorCode.MALFORMED_REQUEST, refused.getErrorCode());
    }

    @Test
    @DisplayName("T129: a malformed request closes the connection, every other error does not")
    void onlyMalformedClosesTheConnection() {
        assertTrue(ErrorCode.MALFORMED_REQUEST.closesConnection());
        for (ErrorCode code : ErrorCode.values()) {
            if (code != ErrorCode.MALFORMED_REQUEST) {
                assertFalse(code.closesConnection(),
                        code + " must leave the connection open, so that one bad "
                                + "request does not force the user to sign in again.");
            }
        }
    }

    @Test
    @DisplayName("T130: a successful response round trips with both server timestamps")
    void okResponseRoundTrips() {
        LocalDateTime receivedAt = LocalDateTime.of(2026, 10, 15, 9, 14, 22, 502_118_000);
        JsonObject payload = new JsonObject();
        payload.addProperty("version", "1.0");

        Response decoded = ProtocolCodec.decodeResponse(ProtocolCodec.encodeResponse(
                Response.ok("req-1", receivedAt, payload)));

        assertTrue(decoded.isOk());
        assertEquals("req-1", decoded.getRequestId());
        assertEquals(receivedAt, decoded.getReceivedAt());
        assertNotNull(decoded.getRespondedAt(),
                "respondedAt is one of the three timestamps the time stamping "
                        + "requirement asks for.");
        assertEquals("1.0", decoded.getPayload().get("version").getAsString());
        assertNull(decoded.getErrorCode());
    }

    @Test
    @DisplayName("T131: a refusal round trips with its code readable as an enum")
    void errorResponseRoundTrips() {
        Response decoded = ProtocolCodec.decodeResponse(ProtocolCodec.encodeResponse(
                Response.error("req-2", LocalDateTime.now(),
                        ErrorCode.ACCESS_DENIED, "Your role does not permit this.")));

        assertFalse(decoded.isOk());
        assertEquals(ErrorCode.ACCESS_DENIED, decoded.errorCodeOrNull());
        assertEquals("Your role does not permit this.", decoded.getMessage());
        assertNull(decoded.getPayload());
    }

    @Test
    @DisplayName("A code this client does not recognise leaves the message readable")
    void unknownErrorCodeDoesNotBreakTheClient() {
        // A server newer than the client can send a code the client has never
        // heard of. The client must still be able to show the message rather
        // than failing on the code.
        String line = "{\"requestId\":\"r\",\"status\":\"ERROR\","
                + "\"errorCode\":\"SOME_FUTURE_CODE\",\"message\":\"Something happened.\"}";

        Response decoded = ProtocolCodec.decodeResponse(line);

        assertNull(decoded.errorCodeOrNull());
        assertEquals("Something happened.", decoded.getMessage());
    }

    @Test
    @DisplayName("A payload serialised, parsed and serialised again is unchanged")
    void canonicalPayloadIsStable() {
        // Signatures are computed over this string on the client and
        // recomputed over it on the server. If the two differed, every signed
        // request would be refused.
        JsonObject payload = new JsonObject();
        payload.addProperty("zebra", 1);
        payload.addProperty("apple", 2);
        payload.addProperty("middle", "value");

        String onTheClient = ProtocolCodec.canonicalPayload(payload);

        Request decoded = ProtocolCodec.decodeRequest(ProtocolCodec.encodeRequest(
                new Request(Operation.SERVER_PING.wireName(), payload)));
        String onTheServer = ProtocolCodec.canonicalPayload(decoded.getPayload());

        assertEquals(onTheClient, onTheServer,
                "Field order must survive the round trip, or signatures would "
                        + "never match.");
    }

    @Test
    @DisplayName("A request built with no payload still has one")
    void payloadIsNeverNull() {
        Request request = new Request(Operation.AUTH_LOGOUT.wireName(), null);
        assertNotNull(request.getPayload());
        assertEquals("{}", ProtocolCodec.canonicalPayload(request.getPayload()));
    }
}
