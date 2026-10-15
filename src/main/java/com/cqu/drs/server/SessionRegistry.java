package com.cqu.drs.server;

import com.cqu.drs.model.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every session currently open, keyed by token.
 *
 * This is the one piece of mutable state the server threads share. It is a
 * ConcurrentHashMap rather than a synchronised map because it is read on every
 * request and written only at sign-in and sign-out, and a ConcurrentHashMap
 * lets concurrent reads proceed without blocking each other.
 *
 * <p>Sessions are held in memory, not in the database. A session is meaningful
 * only while the server that issued it is running: its signing key exists
 * nowhere else, so a session could not be resumed after a restart even if it
 * had been stored. Restarting the server therefore signs everybody out, which
 * is the honest behaviour rather than a limitation worked around.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Opens a session for a user who has just authenticated.
     *
     * @param user the authenticated user
     * @return the new session
     */
    public Session open(User user) {
        return open(new Session(user));
    }

    /**
     * Registers a session built elsewhere.
     *
     * Used by the tests, which build sessions against a fixed clock so that
     * the idle timeout can be exercised without waiting for it.
     *
     * @param session the session to register
     * @return the same session
     */
    public Session open(Session session) {
        sessions.put(session.getToken(), session);
        return session;
    }

    /**
     * Finds a live session by its token.
     *
     * A session found to have expired is removed here rather than left for a
     * sweep to find later, so an expired token cannot be used in the window
     * between expiring and being cleaned up.
     *
     * @param token the token presented by the client
     * @return the session, or an empty Optional when the token is unknown or
     *         its session has expired
     */
    public Optional<Session> find(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.hasExpired()) {
            sessions.remove(token, session);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Ends a session.
     *
     * @param token the token to close
     * @return the session that was closed, or an empty Optional when the token
     *         was already unknown
     */
    public Optional<Session> close(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.remove(token));
    }

    /**
     * How many sessions are open.
     *
     * @return the count
     */
    public int count() {
        return sessions.size();
    }

    /**
     * Every open session, for an administrator view and for tests.
     *
     * @return the sessions, as an unmodifiable view
     */
    public Collection<Session> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Removes every session that has passed its idle timeout.
     *
     * @return how many were removed
     */
    public int removeExpired() {
        int before = sessions.size();
        sessions.values().removeIf(Session::hasExpired);
        return before - sessions.size();
    }
}
