package com.cqu.drs.protocol;

/**
 * The reasons a request can be refused, as defined in section 6 of
 * PROTOCOL.md.
 *
 * A code is sent instead of a free text message so that the client can decide
 * what to do without reading English. The accompanying message is for the
 * person at the keyboard; the code is for the program.
 */
public enum ErrorCode {

    /** The operation named is not one the protocol defines. */
    UNKNOWN_OPERATION,

    /**
     * The line was not valid JSON, or an envelope field the protocol requires
     * was absent. This is the only error that closes the connection, because
     * after it the server cannot know where the next message begins.
     */
    MALFORMED_REQUEST,

    /** The operation requires a session and no valid token was presented. */
    NOT_AUTHENTICATED,

    /** The token was recognised but its session has ended. */
    SESSION_EXPIRED,

    /**
     * The session's role is not permitted this operation, or the record asked
     * for is outside the session's department.
     */
    ACCESS_DENIED,

    /** The signature did not match the canonical form of the request. */
    SIGNATURE_INVALID,

    /** This request identifier has already been handled. */
    REPLAY_DETECTED,

    /** The payload broke a domain rule. The message names the rule. */
    VALIDATION_FAILED,

    /** No record matches the identifier given. */
    NOT_FOUND,

    /**
     * The operation lost a race against another client, such as a resource
     * already allocated to a different disaster.
     */
    CONFLICT,

    /** The database rejected the operation or could not be reached. */
    STORAGE_FAILURE,

    /**
     * An unexpected fault. The detail is written to the server log and is
     * deliberately not returned, because a stack trace or an SQL fragment
     * tells a client about the schema.
     */
    INTERNAL_ERROR;

    /**
     * Whether a connection can continue after this error.
     *
     * Every refusal except a malformed message leaves the connection usable,
     * so a client that sends one bad request does not have to reconnect and
     * sign in again.
     *
     * @return true when the connection should be closed
     */
    public boolean closesConnection() {
        return this == MALFORMED_REQUEST;
    }
}
