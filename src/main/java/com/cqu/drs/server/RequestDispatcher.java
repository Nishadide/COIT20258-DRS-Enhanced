package com.cqu.drs.server;

import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.protocol.RequestSigner;
import com.cqu.drs.protocol.Response;
import com.cqu.drs.repository.RequestLogEntry;
import com.cqu.drs.repository.RequestLogRepository;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Decides what happens to a request, and in what order the checks are made.
 *
 * The order is the design. Each check is cheap and refuses a class of request
 * that the next check would otherwise have to cope with, and every one of them
 * happens on the server where a modified client cannot reach it.
 *
 * <ol>
 *   <li><b>Record and claim.</b> The request is written to the request log
 *       before anything is decided, so that an attempt that is about to be
 *       refused is recorded rather than lost. The insert also claims the
 *       request identifier, and a claim that fails means the identifier has
 *       been used before, so the request is a replay and is refused before it
 *       can have any effect.</li>
 *   <li><b>Operation.</b> A name the protocol does not define is refused.</li>
 *   <li><b>Session.</b> An operation that is not public needs one. A token
 *       that was never issued and a token whose session has ended are
 *       reported differently, because the first means the client is confused
 *       and the second means the person should sign in again.</li>
 *   <li><b>Role.</b> Checked against the role recorded on the session at
 *       sign-in, never against anything the request carries.</li>
 *   <li><b>Signature.</b> Checked last of the cheap checks, because it is the
 *       only one that costs a hash.</li>
 *   <li><b>Handler.</b> Runs, and any refusal it raises is turned into a
 *       response.</li>
 * </ol>
 *
 * <p>Access control and signature verification deliberately happen here rather
 * than in each handler. A check that every handler has to remember to make is
 * a check that one handler will eventually forget.
 */
public final class RequestDispatcher {

    private final Map<Operation, OperationHandler> handlers = new EnumMap<>(Operation.class);
    private final SessionRegistry sessions;
    private final RequestLogRepository requestLog;

    /**
     * Creates a dispatcher.
     *
     * @param sessions   the open sessions
     * @param requestLog where requests are recorded
     */
    public RequestDispatcher(SessionRegistry sessions, RequestLogRepository requestLog) {
        if (sessions == null || requestLog == null) {
            throw new IllegalArgumentException(
                    "A dispatcher needs a session registry and a request log.");
        }
        this.sessions = sessions;
        this.requestLog = requestLog;
    }

    /**
     * Registers a handler.
     *
     * @param handler the handler
     * @throws IllegalStateException when a handler is already registered for
     *         the same operation, because which of the two would run would
     *         otherwise depend on registration order
     */
    public RequestDispatcher register(OperationHandler handler) {
        OperationHandler existing = handlers.put(handler.operation(), handler);
        if (existing != null) {
            throw new IllegalStateException("Two handlers are registered for "
                    + handler.operation().wireName() + ": "
                    + existing.getClass().getSimpleName() + " and "
                    + handler.getClass().getSimpleName());
        }
        return this;
    }

    /**
     * Whether an operation has a handler yet.
     *
     * @param operation the operation
     * @return true when a handler is registered
     */
    public boolean handles(Operation operation) {
        return handlers.containsKey(operation);
    }

    /**
     * How many operations have handlers.
     *
     * @return the count
     */
    public int handlerCount() {
        return handlers.size();
    }

    /**
     * Carries out one request and produces the response to send back.
     *
     * This method does not throw. Every failure becomes a response, because a
     * client that sent a request is owed an answer whatever went wrong.
     *
     * @param request       the request, already parsed and envelope-checked
     * @param receivedAt    the server's clock when the request was read
     * @param clientAddress the address the connection came from, for the log
     * @return the response to write back
     */
    public Response dispatch(Request request, LocalDateTime receivedAt,
                             String clientAddress) {
        Optional<Session> session = sessions.find(request.getSessionToken());

        boolean claimed;
        try {
            claimed = claim(request, receivedAt, clientAddress, session);
        } catch (RuntimeException e) {
            // The request log could not be written. The request is refused
            // rather than carried out unrecorded, because an action with no
            // record of who asked for it defeats the point of keeping one.
            // Nothing is completed, because nothing was recorded.
            return Response.error(request.getRequestId(), receivedAt,
                    ErrorCode.STORAGE_FAILURE,
                    "The request could not be recorded, so it was not carried out.");
        }

        if (!claimed) {
            // A replay. The row bearing this identifier belongs to the request
            // that was handled first, and this one must not touch it.
            //
            // Completing it here was a real defect, found by running the
            // server against a database rather than by reading the code: the
            // replay overwrote the original row's outcome, so replaying a
            // successful request turned its record into a refusal. That would
            // have let anyone who captured a request destroy the evidence of
            // the original simply by sending it again, which is the opposite
            // of what this log exists to do.
            return Response.error(request.getRequestId(), receivedAt,
                    ErrorCode.REPLAY_DETECTED,
                    "This request has already been handled. Send a new "
                            + "request rather than repeating this one.");
        }

        Response response = decide(request, receivedAt, session);

        requestLog.complete(request.getRequestId(),
                response.getStatus().name(),
                response.getErrorCode(),
                response.getRespondedAt());

        return response;
    }

    private Response decide(Request request, LocalDateTime receivedAt,
                            Optional<Session> session) {
        Optional<Operation> found = Operation.fromWireName(request.getOperation());
        if (found.isEmpty()) {
            return Response.error(request.getRequestId(), receivedAt,
                    ErrorCode.UNKNOWN_OPERATION,
                    "This server does not support the operation "
                            + request.getOperation() + ".");
        }
        Operation operation = found.get();

        if (!operation.isPublic()) {
            if (session.isEmpty()) {
                boolean tokenOffered = request.getSessionToken() != null
                        && !request.getSessionToken().isEmpty();
                return tokenOffered
                        ? Response.error(request.getRequestId(), receivedAt,
                                ErrorCode.SESSION_EXPIRED,
                                "Your session has ended. Please sign in again.")
                        : Response.error(request.getRequestId(), receivedAt,
                                ErrorCode.NOT_AUTHENTICATED,
                                "You must sign in before doing this.");
            }

            if (!operation.permits(session.get().getRole())) {
                // The message names neither the role required nor any record,
                // so a refusal tells the caller nothing they did not already
                // know.
                return Response.error(request.getRequestId(), receivedAt,
                        ErrorCode.ACCESS_DENIED,
                        "Your role does not permit this.");
            }

            if (!RequestSigner.verify(session.get().getSigningKey(), request)) {
                return Response.error(request.getRequestId(), receivedAt,
                        ErrorCode.SIGNATURE_INVALID,
                        "The request could not be verified. Please sign in again.");
            }
        }

        OperationHandler handler = handlers.get(operation);
        if (handler == null) {
            // The operation is part of the agreed protocol but its handler has
            // not been written yet. Saying so is accurate; reporting it as an
            // unknown operation would not be, and would send whoever hit it
            // looking in the wrong place.
            return Response.error(request.getRequestId(), receivedAt,
                    ErrorCode.INTERNAL_ERROR,
                    "The operation " + operation.wireName()
                            + " is defined but is not available on this server yet.");
        }

        session.ifPresent(Session::touch);

        try {
            JsonObject payload = handler.handle(request, session.orElse(null));
            return Response.ok(request.getRequestId(), receivedAt, payload);

        } catch (ProtocolException e) {
            return Response.error(request.getRequestId(), receivedAt,
                    e.getErrorCode(), e.getMessage());

        } catch (RuntimeException e) {
            // Anything a handler failed to anticipate. The detail goes to the
            // server log and not to the client, because a stack trace or an
            // SQL fragment describes the schema to whoever receives it.
            System.err.println("Unhandled failure in " + operation.wireName()
                    + " for request " + request.getRequestId());
            e.printStackTrace();
            return Response.error(request.getRequestId(), receivedAt,
                    ErrorCode.INTERNAL_ERROR,
                    "The server could not complete this request.");
        }
    }

    private boolean claim(Request request, LocalDateTime receivedAt,
                          String clientAddress, Optional<Session> session) {
        return requestLog.claim(new RequestLogEntry(
                request.getRequestId(),
                request.getSessionToken(),
                session.map(Session::getUsername).orElse(null),
                request.getOperation(),
                clientAddress,
                request.getSentAt(),
                receivedAt,
                request.getSignature()));
    }

    /**
     * Builds the response for a request too malformed to be dispatched.
     *
     * Kept here so that the single place that decides what a response looks
     * like is this class, even for a request that never reached a handler.
     *
     * @param receivedAt when the line was read
     * @param message    what was wrong with it
     * @return the response to write before closing the connection
     */
    public static Response malformed(LocalDateTime receivedAt, String message) {
        return Response.error(null, receivedAt, ErrorCode.MALFORMED_REQUEST, message);
    }
}
