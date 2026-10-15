package com.cqu.drs.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A request log that keeps everything in memory, for tests.
 *
 * This is the reason {@link RequestLogRepository} is an interface. Every other
 * test in this project runs without a database, and making the server's tests
 * the exception would mean the suite could only be run on a machine with MySQL
 * installed and configured, which is three machines in this team and an
 * unknown number for whoever marks it.
 *
 * <p>It reproduces the behaviour the dispatcher depends on, which is that
 * claiming an identifier already claimed returns false. It also records
 * everything it was given so a test can assert what was logged, and can be
 * told to fail, so the dispatcher's behaviour when the log is unwritable can
 * be tested without breaking a real database.
 */
public final class InMemoryRequestLogRepository implements RequestLogRepository {

    private final Map<String, RequestLogEntry> claimed = new LinkedHashMap<>();
    private final Map<String, String> statuses = new LinkedHashMap<>();
    private final Map<String, String> errorCodes = new LinkedHashMap<>();
    private final List<String> completedInOrder = new ArrayList<>();

    private boolean failOnClaim;

    @Override
    public synchronized boolean claim(RequestLogEntry entry) {
        if (failOnClaim) {
            throw new IllegalStateException("The request log is unavailable.");
        }
        if (claimed.containsKey(entry.requestId())) {
            return false;
        }
        claimed.put(entry.requestId(), entry);
        return true;
    }

    @Override
    public synchronized void complete(String requestId, String status, String errorCode,
                                      LocalDateTime respondedAt) {
        statuses.put(requestId, status);
        if (errorCode != null) {
            errorCodes.put(requestId, errorCode);
        }
        completedInOrder.add(requestId);
    }

    @Override
    public synchronized int count() {
        return claimed.size();
    }

    /**
     * Makes the next claim throw, as a database that cannot be reached would.
     *
     * @param failOnClaim true to fail
     */
    public synchronized void setFailOnClaim(boolean failOnClaim) {
        this.failOnClaim = failOnClaim;
    }

    /**
     * What was recorded when a request arrived.
     *
     * @param requestId the identifier
     * @return the entry, or an empty Optional when it was never claimed
     */
    public synchronized Optional<RequestLogEntry> entryFor(String requestId) {
        return Optional.ofNullable(claimed.get(requestId));
    }

    /**
     * The status a request was completed with.
     *
     * @param requestId the identifier
     * @return OK, ERROR, or null when it was never completed
     */
    public synchronized String statusOf(String requestId) {
        return statuses.get(requestId);
    }

    /**
     * The error code a request was completed with.
     *
     * @param requestId the identifier
     * @return the code, or null
     */
    public synchronized String errorCodeOf(String requestId) {
        return errorCodes.get(requestId);
    }

    /**
     * How many requests were completed.
     *
     * @return the count
     */
    public synchronized int completedCount() {
        return completedInOrder.size();
    }

    /**
     * Every identifier claimed, in the order they arrived.
     *
     * @return the identifiers
     */
    public synchronized List<String> claimedIds() {
        return Collections.unmodifiableList(new ArrayList<>(claimed.keySet()));
    }
}
