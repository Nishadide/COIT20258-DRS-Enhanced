package com.cqu.drs.server;

import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.protocol.Response;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T174 to T181: the server over real sockets.
 *
 * These are the only tests in the project that open a network connection.
 * Everything else can be tested by calling a method, but the three things this
 * class covers cannot: that a connection is accepted at all, that several
 * clients really are served at the same time rather than one after another,
 * and that the framing survives a round trip through a socket.
 *
 * <p>Every server here is given port 0, which asks the operating system for a
 * free port. A fixed port would make these tests fail on any machine already
 * running something on it, including a machine running the real server, which
 * is exactly the machine a developer would be testing on.
 */
@DisplayName("DrsServer over real sockets")
class DrsServerTest {

    private static final int TIMEOUT_MS = 5000;

    private final List<DrsServer> started = new ArrayList<>();

    @AfterEach
    void stopServers() {
        // A server left running would hold its port and its threads for the
        // rest of the suite.
        for (DrsServer server : started) {
            server.stop();
        }
        started.clear();
    }

    private DrsServer startServer(RequestDispatcher dispatcher, int poolSize) throws IOException {
        DrsServer server = new DrsServer(0, poolSize, dispatcher);
        server.start();
        started.add(server);
        return server;
    }

    private RequestDispatcher dispatcherWith(OperationHandler handler) {
        return new RequestDispatcher(new SessionRegistry(), new InMemoryRequestLogRepository())
                .register(handler);
    }

    /**
     * A client that opens a connection, sends lines and reads replies, written
     * against the protocol rather than against our client classes, so these
     * tests would still pass if the Java client were rewritten.
     */
    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        TestClient(int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        Response send(Request request) throws IOException {
            out.println(ProtocolCodec.encodeRequest(request));
            String line = in.readLine();
            return line == null ? null : ProtocolCodec.decodeResponse(line);
        }

        Response sendRaw(String line) throws IOException {
            out.println(line);
            String reply = in.readLine();
            return reply == null ? null : ProtocolCodec.decodeResponse(reply);
        }

        String readLine() throws IOException {
            return in.readLine();
        }

        void writeRaw(String line) {
            out.println(line);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static Request ping() {
        return new Request(Operation.SERVER_PING.wireName(), new JsonObject());
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T174: the server binds a port and reports the one it got")
    void serverBindsAPort() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 4);

        assertTrue(server.isRunning());
        assertTrue(server.getPort() > 0,
                "A server given port 0 must report the port it was actually given.");
    }

    @Test
    @DisplayName("T175: a request sent over a socket is answered over the same socket")
    void requestIsAnsweredOverTheSocket() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 4);

        try (TestClient client = new TestClient(server.getPort())) {
            Request request = ping();
            Response response = client.send(request);

            assertTrue(response.isOk());
            assertEquals(request.getRequestId(), response.getRequestId(),
                    "The response must carry the identifier of the request it answers.");
            assertEquals("1.0", response.getPayload().get("version").getAsString());
        }
    }

    @Test
    @DisplayName("T176: one connection serves many requests without reconnecting")
    void connectionIsReusedForManyRequests() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 4);

        try (TestClient client = new TestClient(server.getPort())) {
            for (int i = 0; i < 5; i++) {
                assertTrue(client.send(ping()).isOk(), "Request " + i + " was refused.");
            }
        }

        assertEquals(1, server.getConnectionsAccepted(),
                "The session holds one connection open, so five requests must "
                        + "not have opened five connections.");
    }

    @Test
    @DisplayName("T177: four clients are served at the same time, not one after another")
    void clientsAreServedConcurrently() throws Exception {
        // This is the test that makes "multi-threaded" a fact rather than a
        // claim. The handler records how many requests are inside it at once.
        // If the server served clients one after another the peak would be 1,
        // whatever the thread pool was configured to hold.
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch allArrived = new CountDownLatch(4);

        DrsServer server = startServer(dispatcherWith(new OperationHandler() {
            @Override
            public Operation operation() {
                return Operation.SERVER_PING;
            }

            @Override
            public JsonObject handle(Request request, Session session) {
                int now = inFlight.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                try {
                    // Wait until every client has arrived. If the server were
                    // serving them one at a time this would never complete and
                    // the await would time out, which is itself the failure.
                    allArrived.countDown();
                    allArrived.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                }
                return new JsonObject();
            }
        }), 4);

        List<Thread> clients = new ArrayList<>();
        List<Boolean> results = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(() -> {
                try (TestClient client = new TestClient(server.getPort())) {
                    results.add(client.send(ping()).isOk());
                } catch (IOException e) {
                    results.add(false);
                }
            });
            clients.add(thread);
            thread.start();
        }
        for (Thread thread : clients) {
            thread.join(TIMEOUT_MS);
        }

        assertEquals(4, results.size(), "Every client must have received a reply.");
        assertFalse(results.contains(false), "Every reply must have succeeded.");
        assertEquals(4, peak.get(),
                "All four requests must have been inside the handler at once. "
                        + "A peak of 1 would mean the server served them in turn.");
        assertEquals(4, server.getConnectionsAccepted());
    }

    @Test
    @DisplayName("T178: a line that is not valid JSON is refused and the connection closes")
    void malformedLineClosesTheConnection() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 4);

        try (TestClient client = new TestClient(server.getPort())) {
            Response response = client.sendRaw("{not json at all");

            assertEquals(ErrorCode.MALFORMED_REQUEST, response.errorCodeOrNull());
            assertNull(client.readLine(),
                    "After a malformed message the server cannot tell where the "
                            + "next one begins, so it must close the connection "
                            + "rather than guess.");
        }
    }

    @Test
    @DisplayName("T179: a blank line is ignored and the connection stays usable")
    void blankLineIsIgnored() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 4);

        try (TestClient client = new TestClient(server.getPort())) {
            client.writeRaw("");
            // A stray newline is not evidence that the stream is out of step,
            // so the connection must survive it.
            assertTrue(client.send(ping()).isOk());
        }
    }

    @Test
    @DisplayName("T180: a refused request leaves the connection open")
    void refusalDoesNotCloseTheConnection() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 4);

        try (TestClient client = new TestClient(server.getPort())) {
            Response refused = client.send(new Request("no.such.operation", new JsonObject()));
            assertEquals(ErrorCode.UNKNOWN_OPERATION, refused.errorCodeOrNull());

            assertTrue(client.send(ping()).isOk(),
                    "One bad request must not force the user to sign in again.");
        }
    }

    @Test
    @DisplayName("T181: stopping the server releases its port")
    void stoppingReleasesThePort() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 2);
        int port = server.getPort();

        server.stop();

        assertFalse(server.isRunning());

        // The port is free, which is proved by binding it again. Without
        // setReuseAddress this can fail for a minute or two after a server
        // stops, which would make restarting the application look broken.
        DrsServer replacement = new DrsServer(port, 2,
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()));
        replacement.start();
        started.add(replacement);

        assertEquals(port, replacement.getPort());
    }

    @Test
    @DisplayName("A server cannot be started twice")
    void serverCannotStartTwice() throws IOException {
        DrsServer server = startServer(
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()), 2);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                server::start);
    }

    @Test
    @DisplayName("Stopping a server that never started is harmless")
    void stoppingAnUnstartedServerIsHarmless() {
        DrsServer server = new DrsServer(0, 2,
                dispatcherWith(new com.cqu.drs.server.handler.PingHandler()));

        server.stop();

        assertFalse(server.isRunning());
        assertEquals(-1, server.getPort());
    }
}
