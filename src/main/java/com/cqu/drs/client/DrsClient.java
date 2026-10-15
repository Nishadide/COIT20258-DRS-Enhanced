package com.cqu.drs.client;

import com.cqu.drs.db.DatabaseConfig;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.protocol.RequestSigner;
import com.cqu.drs.protocol.Response;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The client's end of the protocol.
 *
 * One of these is held for the life of the application. It owns the socket,
 * the session token and the signing key, and it is the only class in the
 * client that knows a network exists. Everything above it works in terms of
 * domain objects.
 *
 * <h2>One connection, held open</h2>
 * The connection is opened on first use and kept, rather than opened per
 * request, because the session and its signing key are bound to it. Opening a
 * connection per request would also mean a sign-in per request.
 *
 * <h2>Why it reconnects once</h2>
 * A connection can be dropped by a server restart or by the network, and the
 * client only finds out when it tries to write. Rather than failing and asking
 * the person to restart the application, a failed send is retried once on a
 * fresh connection. A second failure is reported, because by then the server
 * really is unreachable.
 *
 * <p>Retrying is safe here only because the retry is a different request: a
 * new request identifier is generated each time. Resending the identical
 * request would be refused by the server as a replay, which is correct, and is
 * why this builds a new one.
 *
 * <h2>Threading</h2>
 * Methods are synchronised. JavaFX calls them from the one user interface
 * thread, so there is no contention in practice, but a background task that
 * refreshed a screen would otherwise be able to interleave its request with
 * one already in flight, and the two replies would be read by the wrong
 * callers.
 */
public final class DrsClient implements AutoCloseable {

    private static final int CONNECT_TIMEOUT_MS = 5000;

    /**
     * How long to wait for a reply before giving up.
     *
     * Without this a client whose server stopped answering would wait for ever
     * and the application would appear frozen with no way out.
     */
    private static final int READ_TIMEOUT_MS = 15000;

    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String sessionToken;
    private String signingKey;

    /**
     * Creates a client for the configured server.
     *
     * @param config where the host and port come from
     */
    public DrsClient(DatabaseConfig config) {
        this.host = config.serverHost();
        this.port = config.serverPort();
    }

    /**
     * Creates a client for a given address, used by the tests.
     *
     * @param host the server host
     * @param port the server port
     */
    public DrsClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Whether a connection is currently open.
     *
     * @return true when connected
     */
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Records the session this client is signed in as.
     *
     * Called after a successful sign-in. The signing key is kept only in
     * memory and is never written anywhere.
     *
     * @param sessionToken the token issued by the server
     * @param signingKey   the key the server issued for signing requests
     */
    public synchronized void setSession(String sessionToken, String signingKey) {
        this.sessionToken = sessionToken;
        this.signingKey = signingKey;
    }

    /**
     * Forgets the session, after signing out or when the server reports that
     * the session has ended.
     */
    public synchronized void clearSession() {
        this.sessionToken = null;
        this.signingKey = null;
    }

    /**
     * The token this client is using, or null when not signed in.
     *
     * @return the session token
     */
    public synchronized String getSessionToken() {
        return sessionToken;
    }

    /**
     * Sends a request and returns whatever the server replied, including a
     * refusal.
     *
     * @param operation the operation to invoke
     * @param payload   the operation's arguments
     * @return the response, which may be a refusal
     * @throws ServerUnavailableException when the server cannot be reached
     */
    public synchronized Response send(Operation operation, JsonObject payload) {
        try {
            return exchange(operation, payload);
        } catch (IOException first) {
            // The connection was probably dropped while idle. Close it and try
            // once on a new one.
            closeQuietly();
            try {
                return exchange(operation, payload);
            } catch (IOException second) {
                closeQuietly();
                throw new ServerUnavailableException(
                        "The DRS server at " + host + ":" + port
                                + " could not be reached. Check that it is running. "
                                + second.getMessage(), second);
            }
        }
    }

    /**
     * Sends a request and returns its payload, turning a refusal into the
     * failure the screens expect.
     *
     * <h2>Why the translation happens here and nowhere else</h2>
     * It began in the remote services, each translating the refusals it
     * expected, and they disagreed: a role refusal arrived as one kind of
     * failure from one operation and as a protocol exception from the next,
     * because an operation with nothing else to catch had nowhere to put the
     * translation. The end-to-end test found it by asking every operation
     * rather than a representative one.
     *
     * <p>Every request in the client passes through this method, so doing it
     * here means no service can be inconsistent with another and none can
     * forget. {@link Refusals} says what each refusal becomes and why.
     *
     * @param operation the operation to invoke
     * @param payload   the operation's arguments
     * @return the response payload
     * @throws IllegalArgumentException   when the request named something
     *                                    unknown or broke a rule
     * @throws IllegalStateException      when this session may not do it
     * @throws ProtocolException          for a refusal the person cannot act
     *                                    on, such as an expired session
     * @throws ServerUnavailableException when the server cannot be reached
     */
    public JsonObject sendOrThrow(Operation operation, JsonObject payload) {
        Response response = send(operation, payload);
        if (!response.isOk()) {
            throw Refusals.translate(refusalOf(response));
        }
        return response.getPayload();
    }

    /**
     * Sends a request for which finding nothing is an ordinary answer.
     *
     * Someone mistyping a reference number is not an error, and neither is
     * asking about a task that has since been removed. Those callers want an
     * empty Optional rather than an exception, and saying so at the call site
     * is clearer than catching a refusal and deciding it was expected.
     *
     * @param operation the operation to invoke
     * @param payload   the operation's arguments
     * @return the response payload, or an empty Optional when the server
     *         reported that there is no such record
     * @throws IllegalStateException      when this session may not do it
     * @throws ProtocolException          for a refusal the person cannot act
     *                                    on
     * @throws ServerUnavailableException when the server cannot be reached
     */
    public Optional<JsonObject> sendAllowingNotFound(Operation operation,
                                                     JsonObject payload) {
        Response response = send(operation, payload);
        if (response.isOk()) {
            return Optional.of(response.getPayload());
        }
        ProtocolException refusal = refusalOf(response);
        if (refusal.getErrorCode() == ErrorCode.NOT_FOUND) {
            return Optional.empty();
        }
        throw Refusals.translate(refusal);
    }

    /**
     * Turns a refused response into the refusal it describes, and reacts to
     * the one refusal that says something about this client's own state.
     *
     * @param response the refused response
     * @return the refusal
     */
    private ProtocolException refusalOf(Response response) {
        ErrorCode code = response.errorCodeOrNull();
        if (code == ErrorCode.SESSION_EXPIRED || code == ErrorCode.NOT_AUTHENTICATED) {
            // The session is gone, so holding the token would mean every later
            // request failed the same way.
            clearSession();
        }
        return new ProtocolException(
                code == null ? ErrorCode.INTERNAL_ERROR : code,
                response.getMessage() == null
                        ? "The server refused the request." : response.getMessage());
    }

    private Response exchange(Operation operation, JsonObject payload) throws IOException {
        ensureConnected();

        Request request = new Request(operation.wireName(), payload);
        if (sessionToken != null) {
            request.setSessionToken(sessionToken);
            request.setSignature(RequestSigner.sign(signingKey, request));
        }

        out.println(ProtocolCodec.encodeRequest(request));
        if (out.checkError()) {
            throw new IOException("The request could not be written to the server.");
        }

        String line = in.readLine();
        if (line == null) {
            throw new IOException("The server closed the connection without replying.");
        }

        Response response = ProtocolCodec.decodeResponse(line);

        if (!request.getRequestId().equals(response.getRequestId())
                && response.getRequestId() != null) {
            // The replies have come back in a different order than the
            // requests went out, which means this connection is no longer
            // trustworthy. Nothing in the protocol allows it, so rather than
            // return someone else's answer, the connection is abandoned.
            throw new IOException("The server replied to a different request ("
                    + response.getRequestId() + " rather than "
                    + request.getRequestId() + ").");
        }

        return response;
    }

    private void ensureConnected() throws IOException {
        if (isConnected()) {
            return;
        }
        Socket fresh = new Socket();
        fresh.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        fresh.setSoTimeout(READ_TIMEOUT_MS);
        // Nagle's algorithm delays a small write waiting for more data to
        // combine with. Every message here is small and is followed by a wait
        // for the reply, so that delay is added to every single request.
        fresh.setTcpNoDelay(true);

        this.socket = fresh;
        this.in = new BufferedReader(new InputStreamReader(
                fresh.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(
                fresh.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Already failing; the reason this close did not work adds nothing.
        } finally {
            socket = null;
            in = null;
            out = null;
        }
    }

    /**
     * Closes the connection. The session is forgotten with it, because a
     * session belongs to a connection.
     */
    @Override
    public synchronized void close() {
        closeQuietly();
        clearSession();
    }

    /**
     * A description of where this client connects, for a status line.
     *
     * @return the host and port
     */
    public String describeServer() {
        return host + ":" + port;
    }
}
