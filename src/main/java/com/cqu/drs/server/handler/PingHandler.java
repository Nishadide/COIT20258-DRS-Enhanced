package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.Request;
import com.cqu.drs.server.OperationHandler;
import com.cqu.drs.server.Session;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Answers server.ping with the server's clock and its protocol version.
 *
 * This is not filler. It is the one operation that can be called before
 * anything else works, so it is what a client uses to find out whether the
 * server is reachable before offering a sign-in screen, and what proves the
 * accept loop, the codec, the dispatcher and the request log all function
 * before a single domain operation exists.
 *
 * <p>Returning the server's clock also gives a client a way to notice that its
 * own clock is wrong, which matters because every request carries a client
 * timestamp that ends up in the request log.
 */
public final class PingHandler implements OperationHandler {

    /** The version of PROTOCOL.md this server implements. */
    public static final String PROTOCOL_VERSION = "1.0";

    @Override
    public Operation operation() {
        return Operation.SERVER_PING;
    }

    @Override
    public JsonObject handle(Request request, Session session) {
        JsonObject payload = new JsonObject();
        payload.addProperty("serverTime",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()));
        payload.addProperty("version", PROTOCOL_VERSION);
        // Whether a session was presented, so a client can tell a live session
        // from an expired one without sending an operation that would change
        // something.
        payload.addProperty("signedIn", session != null);
        return payload;
    }
}
