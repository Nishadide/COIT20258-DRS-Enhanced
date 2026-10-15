package com.cqu.drs.server;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.RequestSigner;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * One signed-in user, held on the server for as long as they stay signed in.
 *
 * The role and the department are copied from the user record at login and
 * read from here on every later request. They are never taken from the
 * request, because a client can send whatever it likes and a role a client
 * asserts is worth nothing.
 *
 * <p>A session is shared between threads. Its identity, role, department and
 * signing key never change after construction and are final, so no
 * synchronisation is needed to read them. Only the last-seen time changes, and
 * it is volatile.
 */
public final class Session {

    /**
     * How long a session may sit idle before it is refused.
     *
     * A session that never expired would leave a usable signing key valid for
     * as long as the server ran, including after the person walked away from
     * the machine.
     */
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /** 24 bytes encoded without padding gives a 32 character token. */
    private static final int TOKEN_LENGTH_BYTES = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String token;
    private final String username;
    private final UserRole role;
    private final String departmentId;
    private final String signingKey;
    private final LocalDateTime openedAt;
    private final Clock clock;

    private volatile LocalDateTime lastSeenAt;

    /**
     * Opens a session for a user who has just signed in.
     *
     * @param user the authenticated user
     */
    public Session(User user) {
        this(user, Clock.systemDefaultZone());
    }

    /**
     * Opens a session that reads the time from a given clock.
     *
     * The clock exists so that the idle timeout can be tested. Waiting thirty
     * minutes inside a test is not an option, and a test that instead reaches
     * in and alters the session's fields would be testing something the
     * running system never does. A fixed clock set in the past lets the real
     * expiry logic run against a time it will actually see.
     *
     * @param user  the authenticated user
     * @param clock where the session reads the current time from
     */
    public Session(User user, Clock clock) {
        if (user == null) {
            throw new IllegalArgumentException("A session needs a user.");
        }
        if (clock == null) {
            throw new IllegalArgumentException("A session needs a clock.");
        }
        this.clock = clock;
        this.token = newToken();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.departmentId = user instanceof DepartmentResponder responder
                ? responder.getDepartmentId()
                : null;
        this.signingKey = RequestSigner.newSigningKey();
        this.openedAt = LocalDateTime.now(clock);
        this.lastSeenAt = this.openedAt;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    /**
     * The department this session is confined to.
     *
     * Only a responder has one. A coordinator or administrator returns null,
     * which means they are not confined to a department rather than that they
     * belong to none.
     *
     * @return the department identifier, or null
     */
    public String getDepartmentId() {
        return departmentId;
    }

    /**
     * The key this session's requests are signed with.
     *
     * Sent to the client once, on the login response, and never again in
     * either direction.
     *
     * @return the signing key, base64 encoded
     */
    public String getSigningKey() {
        return signingKey;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    /**
     * Records that a request arrived on this session, which restarts the idle
     * timeout.
     */
    public void touch() {
        this.lastSeenAt = LocalDateTime.now(clock);
    }

    /**
     * Whether this session has been idle longer than the timeout allows.
     *
     * @return true when the session should be refused and discarded
     */
    public boolean hasExpired() {
        return Duration.between(lastSeenAt, LocalDateTime.now(clock))
                .compareTo(IDLE_TIMEOUT) > 0;
    }

    /**
     * Whether this session may act on records belonging to a department.
     *
     * A responder is confined to their own. Anyone else permitted the
     * operation at all is not confined, because the operation level check has
     * already decided they may see across departments.
     *
     * @param otherDepartmentId the department the record belongs to
     * @return true when this session may act on it
     */
    public boolean mayActOnDepartment(String otherDepartmentId) {
        if (role != UserRole.RESPONDER) {
            return true;
        }
        return departmentId != null && departmentId.equals(otherDepartmentId);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_LENGTH_BYTES];
        RANDOM.nextBytes(bytes);
        // URL-safe and unpadded so the token is plain text in a JSON message
        // and in the request_log column, which is 40 characters wide.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A description for the server log. The token and the signing key are
     * deliberately absent: a log is often pasted into a report or an issue,
     * and either value would be enough to impersonate the session.
     *
     * @return a one line description
     */
    @Override
    public String toString() {
        return username + " (" + role.name() + ")"
                + (departmentId == null ? "" : " of " + departmentId);
    }
}
