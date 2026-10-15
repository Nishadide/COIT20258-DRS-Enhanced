package com.cqu.drs.server;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.protocol.RequestSigner;
import com.cqu.drs.protocol.Response;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.cqu.drs.repository.RequestLogEntry;
import com.cqu.drs.server.handler.PingHandler;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T159 to T173: the order and the strictness of the checks every request
 * passes through.
 *
 * This is where access control is enforced, so these are the tests that decide
 * whether the security requirements are met in fact rather than on paper. Each
 * one asks the dispatcher to do something it must refuse, and checks both that
 * it refused and that it gave the right reason, because a refusal with the
 * wrong code sends the person looking in the wrong place.
 */
@DisplayName("RequestDispatcher")
class RequestDispatcherTest {

    private static final String PASSWORD = "Str0ngPass";
    private static final String CLIENT = "127.0.0.1";

    private SessionRegistry sessions;
    private InMemoryRequestLogRepository requestLog;
    private RequestDispatcher dispatcher;
    private AtomicInteger handlerCalls;

    @BeforeEach
    void setUp() {
        sessions = new SessionRegistry();
        requestLog = new InMemoryRequestLogRepository();
        handlerCalls = new AtomicInteger();

        dispatcher = new RequestDispatcher(sessions, requestLog)
                .register(new PingHandler())
                .register(new CountingHandler(Operation.ASSESSMENT_ASSESS, handlerCalls));
    }

    // ---- helpers -------------------------------------------------------

    private static User coordinator() {
        return new EmergencyCoordinator("u-1", "coordinator", PASSWORD,
                "C. Ordinator", "EC-0001");
    }

    private static User responder() {
        return new DepartmentResponder("u-2", "fire.crew", PASSWORD, "Fire Crew", "DEP-01");
    }

    private static User administrator() {
        return new SystemAdministrator("u-3", "admin", PASSWORD, "A. Dmin");
    }

    private Response send(Request request) {
        return dispatcher.dispatch(request, LocalDateTime.now(), CLIENT);
    }

    private static Request signedAssess(Session session) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", "DR-0001");
        payload.addProperty("severity", "HIGH");

        Request request = new Request(Operation.ASSESSMENT_ASSESS.wireName(), payload);
        request.setSessionToken(session.getToken());
        request.setSignature(RequestSigner.sign(session.getSigningKey(), request));
        return request;
    }

    /** A handler that records how often it ran. */
    private static final class CountingHandler implements OperationHandler {
        private final Operation operation;
        private final AtomicInteger calls;

        CountingHandler(Operation operation, AtomicInteger calls) {
            this.operation = operation;
            this.calls = calls;
        }

        @Override
        public Operation operation() {
            return operation;
        }

        @Override
        public JsonObject handle(Request request, Session session) {
            calls.incrementAndGet();
            JsonObject payload = new JsonObject();
            payload.addProperty("handled", true);
            return payload;
        }
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T159: a public operation is answered with no session at all")
    void publicOperationNeedsNoSession() {
        Response response = send(new Request(Operation.SERVER_PING.wireName(), new JsonObject()));

        assertTrue(response.isOk());
        assertEquals("1.0", response.getPayload().get("version").getAsString());
        assertFalse(response.getPayload().get("signedIn").getAsBoolean());
    }

    @Test
    @DisplayName("T160: an operation the protocol does not define is refused")
    void unknownOperationRefused() {
        Response response = send(new Request("report.deleteEverything", new JsonObject()));

        assertEquals(ErrorCode.UNKNOWN_OPERATION, response.errorCodeOrNull());
    }

    @Test
    @DisplayName("T161: a protected operation with no token is refused as not authenticated")
    void noTokenIsNotAuthenticated() {
        Response response = send(
                new Request(Operation.ASSESSMENT_ASSESS.wireName(), new JsonObject()));

        assertEquals(ErrorCode.NOT_AUTHENTICATED, response.errorCodeOrNull());
        assertEquals(0, handlerCalls.get(), "The handler must not have run.");
    }

    @Test
    @DisplayName("T162: a token that is not recognised is reported as an ended session")
    void unknownTokenIsExpiredSession() {
        // Distinguished from NOT_AUTHENTICATED deliberately. A client that
        // sent no token is confused; a client whose token is no longer
        // recognised should tell the person to sign in again.
        Request request = new Request(Operation.ASSESSMENT_ASSESS.wireName(), new JsonObject());
        request.setSessionToken("a-token-that-was-never-issued");

        assertEquals(ErrorCode.SESSION_EXPIRED, send(request).errorCodeOrNull());
    }

    @Test
    @DisplayName("T163: a role that is not permitted the operation is refused")
    void wrongRoleIsAccessDenied() {
        // A responder signing in correctly and then asking to assess a
        // disaster, which only a coordinator may do.
        Session session = sessions.open(responder());

        Response response = send(signedAssess(session));

        assertEquals(ErrorCode.ACCESS_DENIED, response.errorCodeOrNull());
        assertEquals(0, handlerCalls.get());
    }

    @Test
    @DisplayName("T164: a refusal names neither the role required nor any record")
    void refusalRevealsNothing() {
        Session session = sessions.open(responder());
        String message = send(signedAssess(session)).getMessage();

        assertFalse(message.contains("COORDINATOR"),
                "Telling the caller which role would work is telling them what "
                        + "to try next.");
        assertFalse(message.contains("DR-0001"),
                "A refusal must not confirm that a record exists.");
    }

    @Test
    @DisplayName("T165: a request whose signature does not match is refused")
    void badSignatureRefused() {
        Session session = sessions.open(coordinator());
        Request request = signedAssess(session);

        // The payload altered after signing, which is what a captured and
        // edited request looks like.
        request.getPayload().addProperty("severity", "SEVERE");

        assertEquals(ErrorCode.SIGNATURE_INVALID, send(request).errorCodeOrNull());
        assertEquals(0, handlerCalls.get());
    }

    @Test
    @DisplayName("T166: a correctly signed request from a permitted role runs")
    void validSignedRequestRuns() {
        Session session = sessions.open(coordinator());

        Response response = send(signedAssess(session));

        assertTrue(response.isOk(), "Refused with: " + response.getMessage());
        assertTrue(response.getPayload().get("handled").getAsBoolean());
        assertEquals(1, handlerCalls.get());
    }

    @Test
    @DisplayName("T167: the same request sent twice is refused the second time")
    void replayIsRefused() {
        Session session = sessions.open(coordinator());
        Request request = signedAssess(session);

        assertTrue(send(request).isOk());

        Response replay = send(request);

        assertEquals(ErrorCode.REPLAY_DETECTED, replay.errorCodeOrNull());
        assertEquals(1, handlerCalls.get(),
                "A replayed request must not reach the handler a second time.");
    }

    @Test
    @DisplayName("A replay must not overwrite the record of the original request")
    void replayDoesNotOverwriteTheOriginalRecord() {
        // Regression test for a defect found by running the server against a
        // real database. The replay was refused correctly, and then the log
        // row was completed using the same request identifier, which
        // overwrote the original request's outcome with the refusal.
        //
        // The consequence was the opposite of what the log is for: anyone who
        // captured a successful request could destroy the evidence that it
        // succeeded simply by sending it again.
        Session session = sessions.open(coordinator());
        Request request = signedAssess(session);

        assertTrue(send(request).isOk());
        assertEquals("OK", requestLog.statusOf(request.getRequestId()));

        send(request);

        assertEquals("OK", requestLog.statusOf(request.getRequestId()),
                "The record of the original request must still say it succeeded.");
        assertNull(requestLog.errorCodeOf(request.getRequestId()),
                "The original request did not fail, so its row must carry no "
                        + "error code.");
        assertEquals(1, requestLog.completedCount(),
                "A replay claims nothing, so it must complete nothing.");
    }

    @Test
    @DisplayName("T168: an operation that is defined but has no handler says so plainly")
    void declaredOperationWithoutHandler() {
        // audit.findAll is part of the agreed protocol but its handler is not
        // written yet. Asked by the role that is permitted it, so that the
        // role check passes and the missing handler is what answers.
        //
        // Reporting this as an unknown operation would send whoever hit it
        // looking for a typo in the operation name, when the name is correct
        // and the work simply has not been done.
        Session session = sessions.open(administrator());
        Request request = new Request(Operation.AUDIT_FIND_ALL.wireName(), new JsonObject());
        request.setSessionToken(session.getToken());
        request.setSignature(RequestSigner.sign(session.getSigningKey(), request));

        Response response = send(request);

        assertEquals(ErrorCode.INTERNAL_ERROR, response.errorCodeOrNull());
        assertTrue(response.getMessage().contains("audit.findAll"));
        assertTrue(response.getMessage().contains("not available"));
    }

    @Test
    @DisplayName("The role check happens before the handler is looked for")
    void roleIsCheckedBeforeTheHandler() {
        // Order matters: a caller who may not invoke an operation must not be
        // able to learn from the refusal whether that operation is implemented
        // yet.
        Session session = sessions.open(responder());
        Request request = new Request(Operation.AUDIT_FIND_ALL.wireName(), new JsonObject());
        request.setSessionToken(session.getToken());
        request.setSignature(RequestSigner.sign(session.getSigningKey(), request));

        assertEquals(ErrorCode.ACCESS_DENIED, send(request).errorCodeOrNull());
    }

    @Test
    @DisplayName("T169: a handler's refusal is passed through with its own code")
    void handlerRefusalIsPassedThrough() {
        RequestDispatcher local = new RequestDispatcher(sessions, requestLog)
                .register(new OperationHandler() {
                    @Override
                    public Operation operation() {
                        return Operation.ASSESSMENT_ASSESS;
                    }

                    @Override
                    public JsonObject handle(Request request, Session session) {
                        throw ProtocolException.notFound("disaster report", "DR-9999");
                    }
                });

        Session session = sessions.open(coordinator());
        Response response = local.dispatch(signedAssess(session), LocalDateTime.now(), CLIENT);

        assertEquals(ErrorCode.NOT_FOUND, response.errorCodeOrNull());
        assertTrue(response.getMessage().contains("DR-9999"));
    }

    @Test
    @DisplayName("T170: an unexpected failure in a handler does not leak its detail")
    void unexpectedFailureIsNotLeaked() {
        RequestDispatcher local = new RequestDispatcher(sessions, requestLog)
                .register(new OperationHandler() {
                    @Override
                    public Operation operation() {
                        return Operation.ASSESSMENT_ASSESS;
                    }

                    @Override
                    public JsonObject handle(Request request, Session session) {
                        throw new IllegalStateException(
                                "Table 'drs_enhanced.assessments' doesn't exist");
                    }
                });

        Session session = sessions.open(coordinator());
        Response response = local.dispatch(signedAssess(session), LocalDateTime.now(), CLIENT);

        assertEquals(ErrorCode.INTERNAL_ERROR, response.errorCodeOrNull());
        assertFalse(response.getMessage().contains("assessments"),
                "A message naming a table describes the schema to whoever "
                        + "receives it.");
        assertFalse(response.getMessage().contains("drs_enhanced"));
    }

    @Test
    @DisplayName("T171: every request is recorded before it is decided")
    void everyRequestIsRecorded() {
        Session session = sessions.open(coordinator());
        Request request = signedAssess(session);

        send(request);

        RequestLogEntry entry = requestLog.entryFor(request.getRequestId()).orElseThrow();
        assertEquals("coordinator", entry.actor());
        assertEquals("assessment.assess", entry.operation());
        assertEquals(CLIENT, entry.clientAddress());
        assertEquals(request.getSignature(), entry.signature(),
                "The signature is kept so the request can be verified afterwards.");
        assertNotNull(entry.receivedAt());
        assertNotNull(entry.sentAt());
        assertEquals("OK", requestLog.statusOf(request.getRequestId()));
    }

    @Test
    @DisplayName("T172: a refused request is recorded with the reason it was refused")
    void refusedRequestIsRecordedWithItsReason() {
        Session session = sessions.open(responder());
        Request request = signedAssess(session);

        send(request);

        assertEquals("ERROR", requestLog.statusOf(request.getRequestId()));
        assertEquals("ACCESS_DENIED", requestLog.errorCodeOf(request.getRequestId()),
                "An attempt that was refused is exactly what an audit needs to "
                        + "keep.");
    }

    @Test
    @DisplayName("T173: a request that cannot be recorded is refused rather than carried out")
    void unrecordableRequestIsRefused() {
        // An action with no record of who asked for it defeats the point of
        // keeping a record, so the request is refused rather than performed
        // invisibly.
        requestLog.setFailOnClaim(true);
        Session session = sessions.open(coordinator());

        Response response = send(signedAssess(session));

        assertEquals(ErrorCode.STORAGE_FAILURE, response.errorCodeOrNull());
        assertEquals(0, handlerCalls.get());
    }

    @Test
    @DisplayName("Registering two handlers for one operation is refused at startup")
    void duplicateHandlerRefused() {
        // Which of the two would run would otherwise depend on registration
        // order, and the one that lost would be silently dead.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> dispatcher.register(new PingHandler()));
        assertTrue(refused.getMessage().contains("server.ping"));
    }

    @Test
    @DisplayName("A successful request restarts the session's idle timeout")
    void successfulRequestTouchesTheSession() {
        Session session = sessions.open(coordinator());
        LocalDateTime before = session.getLastSeenAt();

        send(signedAssess(session));

        assertFalse(session.getLastSeenAt().isBefore(before));
    }

    @Test
    @DisplayName("A dispatcher cannot be built without a session registry or a request log")
    void dispatcherRequiresItsCollaborators() {
        assertThrows(IllegalArgumentException.class,
                () -> new RequestDispatcher(null, requestLog));
        assertThrows(IllegalArgumentException.class,
                () -> new RequestDispatcher(sessions, null));
    }
}
