package com.cqu.drs.client;

/**
 * Thrown when the client cannot reach the server at all.
 *
 * Distinct from a refusal. A refusal is the server answering, having decided
 * not to do what was asked, and the person needs to know what was wrong with
 * their request. This means nothing answered, and the person needs to know the
 * server is not running or not reachable, which is a different problem with a
 * different remedy.
 *
 * Collapsing the two would mean a screen telling someone their report was
 * invalid when in fact nobody was listening.
 */
public class ServerUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what was being attempted, and where
     * @param cause   the underlying network failure
     */
    public ServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
