package com.cqu.drs.server;

import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.protocol.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Serves one client for as long as it stays connected.
 *
 * One instance runs on one thread, taken from the server's pool, and it is the
 * only thing touching its socket. It holds no state shared with any other
 * handler, which is what makes the server safe to run multi-threaded without
 * locks: everything two clients might contend over lives in the database or in
 * the session registry, and both of those manage their own concurrency.
 *
 * <p>The connection is held open for the whole session rather than opened per
 * request, because the session and its signing key are bound to it.
 */
public final class ClientHandler implements Runnable {

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final String clientAddress;

    /**
     * Creates a handler for an accepted connection.
     *
     * @param socket     the accepted socket
     * @param dispatcher where requests are sent
     */
    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.clientAddress = describe(socket);
    }

    @Override
    public void run() {
        // UTF-8 is named explicitly on both streams. Left to the platform
        // default, a client on a machine with a different default would
        // exchange mangled text for any character outside ASCII, and the fault
        // would appear only for some users and only for some content.
        try (Socket open = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(
                     open.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(
                     open.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    // A blank line carries no request. Ignoring it is kinder
                    // than closing the connection, since a stray newline is
                    // not evidence that the stream is out of step.
                    continue;
                }
                if (!handleLine(line, out)) {
                    break;
                }
            }

        } catch (SocketException e) {
            // The client went away, which is ordinary. A client that closes
            // its window is not a fault worth a stack trace.
        } catch (IOException e) {
            System.err.println("Connection with " + clientAddress + " failed: "
                    + e.getMessage());
        }
    }

    /**
     * Handles one received line.
     *
     * @param line the line read
     * @param out  where the response is written
     * @return true to keep reading, false to close the connection
     */
    private boolean handleLine(String line, PrintWriter out) {
        LocalDateTime receivedAt = LocalDateTime.now();

        Request request;
        try {
            request = ProtocolCodec.decodeRequest(line);
        } catch (ProtocolException e) {
            // A message that could not be parsed is the one failure that ends
            // the connection. Every other refusal leaves it open, but after a
            // malformed message the server cannot tell where the next one
            // begins, so continuing would mean guessing.
            out.println(ProtocolCodec.encodeResponse(
                    RequestDispatcher.malformed(receivedAt, e.getMessage())));
            return false;
        }

        Response response = dispatcher.dispatch(request, receivedAt, clientAddress);
        out.println(ProtocolCodec.encodeResponse(response));
        return true;
    }

    private static String describe(Socket socket) {
        if (socket == null || socket.getInetAddress() == null) {
            return "unknown";
        }
        // Address only, without the port, because the port changes on every
        // connection and adds nothing when reading the log.
        return socket.getInetAddress().getHostAddress();
    }
}
