package com.cqu.drs.repository;

import java.time.LocalDateTime;

/**
 * One request as it is recorded when the server receives it.
 *
 * Carried as a record rather than eight separate parameters, which at that
 * number are easy to pass in the wrong order and impossible to read at the
 * call site.
 *
 * Every field is a String or a timestamp. Nothing here refers to the protocol
 * classes, so the data tier does not depend on the layer above it.
 *
 * @param requestId     the client-generated identifier, unique for all time
 * @param sessionToken  the session the request arrived on, or null when the
 *                      operation is public
 * @param actor         the username the session belongs to, or null
 * @param operation     the wire name of the operation
 * @param clientAddress the address the connection came from
 * @param sentAt        the client's clock when it sent the request, recorded
 *                      but never trusted as the time of record
 * @param receivedAt    the server's clock when it read the request
 * @param signature     the HMAC the client computed, kept so that the request
 *                      can be verified afterwards
 */
public record RequestLogEntry(String requestId,
                              String sessionToken,
                              String actor,
                              String operation,
                              String clientAddress,
                              LocalDateTime sentAt,
                              LocalDateTime receivedAt,
                              String signature) {
}
