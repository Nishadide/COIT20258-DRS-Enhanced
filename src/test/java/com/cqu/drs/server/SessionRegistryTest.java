package com.cqu.drs.server;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T149 to T158: sessions and the registry that holds them.
 *
 * The role and the department carried by a session are the only things the
 * access checks consult, so the tests that matter are the ones proving those
 * come from the user record rather than from anything a client could send.
 */
@DisplayName("Sessions")
class SessionRegistryTest {

    private static final String PASSWORD = "Str0ngPass";

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    private static User coordinator() {
        return new EmergencyCoordinator("u-1", "coordinator", PASSWORD,
                "C. Ordinator", "EC-0001");
    }

    private static User responder(String departmentId) {
        return new DepartmentResponder("u-2", "fire.crew", PASSWORD,
                "Fire Crew", departmentId);
    }

    private static User administrator() {
        return new SystemAdministrator("u-3", "admin", PASSWORD, "A. Dmin");
    }

    @Test
    @DisplayName("T149: opening a session issues a token and a signing key")
    void openIssuesTokenAndKey() {
        Session session = registry.open(coordinator());

        assertNotEquals(null, session.getToken());
        assertNotEquals(null, session.getSigningKey());
        assertEquals("coordinator", session.getUsername());
        assertEquals(UserRole.COORDINATOR, session.getRole());
        assertEquals(1, registry.count());
    }

    @Test
    @DisplayName("T150: two sessions never share a token or a signing key")
    void sessionsAreDistinct() {
        Session first = registry.open(coordinator());
        Session second = registry.open(administrator());

        assertNotEquals(first.getToken(), second.getToken(),
                "A shared token would let one user act as another.");
        assertNotEquals(first.getSigningKey(), second.getSigningKey(),
                "A shared key would let one session sign for another.");
        assertEquals(2, registry.count());
    }

    @Test
    @DisplayName("T151: a session is found by its token")
    void sessionFoundByToken() {
        Session opened = registry.open(coordinator());
        assertEquals(opened.getToken(),
                registry.find(opened.getToken()).orElseThrow().getToken());
    }

    @Test
    @DisplayName("T152: an unknown, empty or absent token finds nothing")
    void unknownTokenFindsNothing() {
        registry.open(coordinator());

        assertTrue(registry.find("not-a-real-token").isEmpty());
        assertTrue(registry.find("").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    @DisplayName("T153: closing a session makes its token unusable")
    void closedSessionCannotBeFound() {
        Session session = registry.open(coordinator());

        assertTrue(registry.close(session.getToken()).isPresent());
        assertTrue(registry.find(session.getToken()).isEmpty(),
                "A token must stop working the moment the user signs out.");
        assertEquals(0, registry.count());
    }

    /**
     * A clock the test can move forward.
     *
     * A fixed clock will not do here. The session reads the time both when it
     * is created and when it is asked whether it has expired, so a clock that
     * always returns the same instant reports no time passing at all and the
     * session never expires. Moving one clock forward is what actually
     * reproduces a session sitting idle.
     */
    private static final class MovableClock extends Clock {
        private Instant instant = Instant.now();

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }
    }

    @Test
    @DisplayName("T154: a session idle past the timeout is refused and discarded")
    void expiredSessionIsRefused() {
        MovableClock clock = new MovableClock();
        Session stale = registry.open(new Session(coordinator(), clock));

        assertFalse(stale.hasExpired(), "A session must start out usable.");

        // Nobody touches it for longer than the timeout allows.
        clock.advance(Session.IDLE_TIMEOUT.plusMinutes(1));

        assertTrue(stale.hasExpired());
        assertTrue(registry.find(stale.getToken()).isEmpty(),
                "An expired token must not be usable.");
        assertEquals(0, registry.count(),
                "Finding an expired session must also remove it, so it cannot "
                        + "be used in the gap before a sweep runs.");
    }

    @Test
    @DisplayName("T155: a session within the timeout is not expired")
    void liveSessionIsNotExpired() {
        Session session = registry.open(coordinator());
        assertFalse(session.hasExpired());
        assertTrue(registry.find(session.getToken()).isPresent());
    }

    @Test
    @DisplayName("T156: a responder's session carries the department from their record")
    void responderSessionCarriesDepartment() {
        Session session = registry.open(responder("DEP-01"));
        assertEquals("DEP-01", session.getDepartmentId());
    }

    @Test
    @DisplayName("T157: a responder may act only on their own department")
    void responderConfinedToOwnDepartment() {
        Session session = registry.open(responder("DEP-01"));

        assertTrue(session.mayActOnDepartment("DEP-01"));
        assertFalse(session.mayActOnDepartment("DEP-02"),
                "This is the check that stops one department updating another's "
                        + "work, and it has to happen on the server.");
        assertFalse(session.mayActOnDepartment(null));
    }

    @Test
    @DisplayName("T158: a coordinator is not confined to any department")
    void coordinatorNotConfinedToADepartment() {
        Session session = registry.open(coordinator());

        assertNull(session.getDepartmentId());
        assertTrue(session.mayActOnDepartment("DEP-01"));
        assertTrue(session.mayActOnDepartment("DEP-08"));
    }

    @Test
    @DisplayName("A sweep removes expired sessions and leaves live ones")
    void sweepRemovesOnlyExpiredSessions() {
        MovableClock clock = new MovableClock();
        registry.open(new Session(coordinator(), clock));
        clock.advance(Session.IDLE_TIMEOUT.plusMinutes(1));

        Session live = registry.open(administrator());

        assertEquals(1, registry.removeExpired());
        assertEquals(1, registry.count());
        assertTrue(registry.find(live.getToken()).isPresent());
    }

    @Test
    @DisplayName("Activity restarts the idle timeout")
    void touchRestartsTheTimeout() {
        Session session = registry.open(coordinator());
        var before = session.getLastSeenAt();

        session.touch();

        assertFalse(session.getLastSeenAt().isBefore(before));
    }

    @Test
    @DisplayName("A session cannot be opened without a user")
    void sessionRequiresAUser() {
        assertThrows(IllegalArgumentException.class, () -> new Session(null));
    }

    @Test
    @DisplayName("A session's description never carries its token or key")
    void descriptionDoesNotLeakCredentials() {
        // Server logs get pasted into reports and issues. Either value would
        // be enough to impersonate the session.
        Session session = registry.open(responder("DEP-01"));
        String description = session.toString();

        assertFalse(description.contains(session.getToken()));
        assertFalse(description.contains(session.getSigningKey()));
        assertTrue(description.contains("fire.crew"));
    }
}
