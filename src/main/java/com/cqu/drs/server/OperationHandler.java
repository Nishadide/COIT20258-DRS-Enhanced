package com.cqu.drs.server;

import com.cqu.drs.protocol.Operation;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.protocol.Request;
import com.google.gson.JsonObject;

/**
 * Carries out one operation.
 *
 * One class per operation rather than a switch over operation names, for two
 * reasons. Each member of the team adds handlers for their own feature without
 * editing a file the others are also editing, and a handler can be constructed
 * with exactly the services it needs instead of every handler being able to
 * reach everything.
 *
 * <p>A handler is called only after the dispatcher has established that the
 * request is not a replay, that the session exists where one is required, that
 * the role is permitted the operation, and that the signature matches. It does
 * not repeat those checks. It does make any check that depends on the specific
 * records being touched, such as a responder reaching a task belonging to a
 * different department, because the dispatcher cannot know which records an
 * operation will touch.
 *
 * <p>Handlers run on the thread of the client that sent the request, so
 * several may run at once. A handler must therefore hold no mutable state of
 * its own between calls.
 */
public interface OperationHandler {

    /**
     * The operation this handler carries out.
     *
     * @return the operation
     */
    Operation operation();

    /**
     * Carries out the request.
     *
     * @param request the request, whose envelope has already been validated
     * @param session the session the request arrived on, or null when the
     *                operation is public and no session was presented
     * @return the payload for the response, or an empty object when the
     *         operation returns nothing
     * @throws ProtocolException when the request cannot be carried out, with
     *         the code to send back
     */
    JsonObject handle(Request request, Session session);
}
