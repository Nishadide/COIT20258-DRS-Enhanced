package com.cqu.drs.client;

import com.cqu.drs.protocol.ProtocolException;

/**
 * Turns a refusal from the server into the failure the screens were written to
 * expect.
 *
 * <h2>Why a translation is needed at all</h2>
 * Each screen talks to an interface with two implementations: the service that
 * reaches a repository in this process, and the one that sends a request to
 * the server. The two are only substitutable if they fail in the same way as
 * well as succeeding in the same way. A controller that caught
 * IllegalArgumentException from the local service and had to catch a protocol
 * exception from the remote one would be a controller that knew which tier it
 * was in, which is the arrangement this design exists to avoid.
 *
 * <p>So a refusal the person is meant to read becomes one of the two
 * exceptions the local services already threw, and the existing catch blocks
 * in the controllers keep working untouched.
 *
 * <h2>Why this is one method rather than one per service</h2>
 * It was five private methods, one in each remote service, and they disagreed:
 * a role refusal came back as one kind of failure from one operation and
 * another kind from the next. The end-to-end test found it, because it asks
 * every operation rather than a representative one. Agreement cannot be
 * maintained by five copies of a decision, so there is now one copy.
 *
 * <h2>What is deliberately not translated</h2>
 * A session that has expired, a signature that did not verify, a replay, a
 * storage failure. None of those can be put right by the person editing a
 * field, and presenting them as though the input were at fault would send
 * someone looking for a mistake they did not make. They travel on as what they
 * are, and the application deals with them by returning to the sign-in screen
 * or reporting that the server is not usable.
 */
final class Refusals {

    /** Not instantiable: this class holds one translation. */
    private Refusals() {
    }

    /**
     * The failure to raise for a refusal.
     *
     * @param e the refusal the server sent
     * @return an IllegalArgumentException when the request named something
     *         that does not exist, is already taken, or breaks a rule about
     *         the values sent; an IllegalStateException when the session is
     *         not permitted to act on what it named; otherwise the refusal
     *         itself
     */
    static RuntimeException translate(ProtocolException e) {
        return switch (e.getErrorCode()) {
            // The request named something unknown, something already taken, or
            // values a rule refuses. All three are faults in what was asked
            // for, and all three are put right by asking for something else.
            case NOT_FOUND, CONFLICT, VALIDATION_FAILED ->
                    new IllegalArgumentException(e.getMessage(), e);

            // Nothing is wrong with the request. This session may not do this,
            // whether because of the role it holds or because the record
            // belongs to another department, and no change to the values would
            // help. The message explains what to do instead, and it names no
            // record the caller is not entitled to know exists.
            case ACCESS_DENIED -> new IllegalStateException(e.getMessage(), e);

            default -> e;
        };
    }
}
