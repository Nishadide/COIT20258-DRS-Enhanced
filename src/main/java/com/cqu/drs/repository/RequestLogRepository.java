package com.cqu.drs.repository;

import java.time.LocalDateTime;

/**
 * Records every request the server handles, with its timestamps and its
 * signature.
 *
 * This serves two of the required security properties at once.
 *
 * <p><b>Time stamping.</b> Three times are kept for each request: the client's
 * own, and the server's on reading it and on answering it. Holding all three
 * means the delay between sending and handling can be measured, and a client
 * whose clock is wrong can be identified rather than quietly corrupting the
 * record.
 *
 * <p><b>Non-repudiation.</b> The signature stored is the one the client
 * computed with the key issued to its session. It can be recomputed
 * afterwards, so a claim that a request was never sent can be tested instead
 * of argued about.
 *
 * <p>It also provides replay detection. {@link #claim} inserts the row before
 * the operation runs and reports whether the identifier was already present,
 * so a replayed request is refused before it has any effect. Because the check
 * is a unique constraint in the database rather than a set held in memory, it
 * survives a restart of the server.
 *
 * <p>This is an interface so that the tests can run without a database, which
 * matters because every other test in this project runs that way.
 */
public interface RequestLogRepository {

    /**
     * Records that a request has been received, and claims its identifier.
     *
     * Called before the operation runs. A false return means this identifier
     * has been handled before and the request must be refused as a replay.
     *
     * A client retrying after a failure must therefore use a new identifier,
     * which is correct: a retry is a fresh instruction, not evidence that the
     * first one happened twice.
     *
     * @param entry the request as received
     * @return true when the identifier was new and the request may proceed
     */
    boolean claim(RequestLogEntry entry);

    /**
     * Records how a request was answered.
     *
     * Until this is called the row says the request was received and never
     * answered, which is exactly what a server stopped part way through a
     * request leaves behind.
     *
     * @param requestId   the identifier claimed earlier
     * @param status      OK or ERROR
     * @param errorCode   the error code when the status is ERROR, otherwise
     *                    null
     * @param respondedAt the server's clock when the reply was written
     */
    void complete(String requestId, String status, String errorCode,
                  LocalDateTime respondedAt);

    /**
     * How many requests have been recorded.
     *
     * @return the count
     */
    int count();
}
