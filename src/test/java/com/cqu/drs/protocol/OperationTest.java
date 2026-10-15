package com.cqu.drs.protocol;

import com.cqu.drs.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T132 to T139: the role table.
 *
 * This enum is PROTOCOL.md section 5 expressed as code. These tests check it
 * says what the document says, because the document is what the other two team
 * members build their clients against, and a quiet disagreement between the two
 * would show up as an access refusal nobody could explain.
 */
@DisplayName("Operation role table")
class OperationTest {

    @Test
    @DisplayName("T132: exactly three operations are public")
    void onlyThreeOperationsArePublic() {
        Set<String> publicOperations = new LinkedHashSet<>();
        for (Operation operation : Operation.values()) {
            if (operation.isPublic()) {
                publicOperations.add(operation.wireName());
            }
        }

        // report.submit is the only public operation that writes, and it is
        // public because the case study requires a member of the public to
        // lodge a report without an account. Anything else appearing here is a
        // hole in the access control.
        assertEquals(Set.of("auth.login", "server.ping", "report.submit"),
                publicOperations,
                "A new public operation must be a deliberate decision, not an "
                        + "accident of copying a nearby constant.");
    }

    @Test
    @DisplayName("T133: no two operations share a wire name")
    void wireNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Operation operation : Operation.values()) {
            assertTrue(seen.add(operation.wireName()),
                    operation.wireName() + " is declared twice. One of the two "
                            + "would be unreachable.");
        }
    }

    @Test
    @DisplayName("T134: an operation is found by the name a request carries")
    void operationFoundByWireName() {
        assertEquals(Operation.REPORT_SUBMIT,
                Operation.fromWireName("report.submit").orElseThrow());
        assertEquals(Operation.ACCOUNT_CREATE,
                Operation.fromWireName("account.create").orElseThrow());
    }

    @Test
    @DisplayName("T135: a name the protocol does not define is not found")
    void unknownNameNotFound() {
        assertTrue(Operation.fromWireName("report.deleteEverything").isEmpty());
        assertTrue(Operation.fromWireName("").isEmpty());
        assertTrue(Operation.fromWireName(null).isEmpty());
    }

    @Test
    @DisplayName("T136: assessing a disaster is for coordinators alone")
    void assessmentIsCoordinatorOnly() {
        Operation assess = Operation.ASSESSMENT_ASSESS;

        assertTrue(assess.permits(UserRole.COORDINATOR));
        assertFalse(assess.permits(UserRole.RESPONDER));
        assertFalse(assess.permits(UserRole.ADMINISTRATOR),
                "An administrator manages accounts. Judging how serious a "
                        + "disaster is belongs to a coordinator.");
    }

    @Test
    @DisplayName("T137: account management is for administrators alone")
    void accountManagementIsAdministratorOnly() {
        for (Operation operation : new Operation[]{
                Operation.ACCOUNT_CREATE, Operation.ACCOUNT_DEACTIVATE,
                Operation.ACCOUNT_ACTIVATE, Operation.ACCOUNT_ASSIGN_ROLE,
                Operation.ACCOUNT_FIND_ALL, Operation.AUDIT_FIND_ALL,
                Operation.AUDIT_VERIFY_CHAIN, Operation.ADMIN_MIGRATE}) {

            assertEquals(Set.of(UserRole.ADMINISTRATOR), operation.allowedRoles(),
                    operation.wireName() + " must be restricted to administrators.");
        }
    }

    @Test
    @DisplayName("T138: a responder may update a task status but may not dispatch one")
    void responderMayUpdateButNotDispatch() {
        assertTrue(Operation.COORDINATION_UPDATE_STATUS.permits(UserRole.RESPONDER));
        assertFalse(Operation.COORDINATION_ALLOCATE.permits(UserRole.RESPONDER),
                "Deciding which departments respond is a coordinator's job.");
    }

    @Test
    @DisplayName("T139: an operation that is not public refuses a request with no role")
    void nonPublicOperationRefusesNoRole() {
        // A null role is what the dispatcher has when no session was found.
        // Permitting it would make every access check pass for a caller who
        // never signed in.
        for (Operation operation : Operation.values()) {
            if (!operation.isPublic()) {
                assertFalse(operation.permits(null),
                        operation.wireName() + " must refuse a caller with no role.");
            }
        }
    }

    @Test
    @DisplayName("A public operation permits anyone, signed in or not")
    void publicOperationPermitsAnyone() {
        assertTrue(Operation.REPORT_SUBMIT.permits(null));
        assertTrue(Operation.REPORT_SUBMIT.permits(UserRole.RESPONDER));
        assertTrue(Operation.SERVER_PING.permits(null));
    }

    @Test
    @DisplayName("Every operation permits at least one role")
    void noOperationIsUnreachable() {
        for (Operation operation : Operation.values()) {
            assertFalse(operation.allowedRoles().isEmpty(),
                    operation.wireName() + " permits nobody, so no client could "
                            + "ever invoke it.");
        }
    }

    @Test
    @DisplayName("Every wire name is lowercase and of the form area.action")
    void wireNamesFollowOneConvention() {
        for (Operation operation : Operation.values()) {
            String name = operation.wireName();
            assertTrue(name.matches("[a-z]+\\.[a-zA-Z]+"),
                    name + " does not follow the area.action convention used by "
                            + "every other operation.");
        }
    }
}
