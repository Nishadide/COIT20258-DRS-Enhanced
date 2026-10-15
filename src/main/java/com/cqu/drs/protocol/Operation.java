package com.cqu.drs.protocol;

import com.cqu.drs.model.UserRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every operation the protocol defines, and the roles permitted to invoke it.
 *
 * This enum is the role table from section 5 of PROTOCOL.md expressed as code,
 * so the document and the enforcement cannot drift apart. The server consults
 * it on every request. The client's screens also hide what a user may not do,
 * but that is a convenience rather than a control: a modified client could
 * send any operation it liked, so the decision has to be made here, on the
 * server, from the role recorded against the session rather than any role the
 * client claims.
 *
 * <p>The wire name is a string rather than the enum constant's own name
 * because the protocol's names contain a dot, which an enum constant cannot.
 * Sending a readable name such as {@code report.submit} also makes a captured
 * message legible without this class to hand.
 *
 * <p>Operations whose handlers are not written yet are still declared here.
 * The enum states the contract the three members agreed, and the handlers fill
 * in behind it. An operation declared with no handler is refused with
 * INTERNAL_ERROR and a message saying so, which is accurate and visible,
 * rather than being reported as an unknown operation, which would not be.
 */
public enum Operation {

    // ---- Session -------------------------------------------------------

    AUTH_LOGIN("auth.login", Access.PUBLIC),
    AUTH_LOGOUT("auth.logout", Access.ANY_SIGNED_IN),
    AUTH_CHANGE_PASSWORD("auth.changePassword", Access.ANY_SIGNED_IN),
    SERVER_PING("server.ping", Access.PUBLIC),

    // ---- Reports -------------------------------------------------------

    /**
     * Public because the case study requires a member of the public to lodge a
     * report without an account. It is the only public operation that writes.
     */
    REPORT_SUBMIT("report.submit", Access.PUBLIC),
    REPORT_FIND_BY_REFERENCE("report.findByReference", Access.ANY_SIGNED_IN),
    REPORT_FIND_ALL("report.findAll", Access.of(UserRole.COORDINATOR, UserRole.ADMINISTRATOR)),
    REPORT_NEARBY("report.nearby", Access.of(UserRole.COORDINATOR, UserRole.ADMINISTRATOR)),

    // ---- Assessment ----------------------------------------------------

    ASSESSMENT_ASSESS("assessment.assess", Access.of(UserRole.COORDINATOR)),
    ASSESSMENT_QUEUE_BY_PRIORITY("assessment.queueByPriority", Access.of(UserRole.COORDINATOR)),
    ASSESSMENT_AWAITING("assessment.awaitingAssessment", Access.of(UserRole.COORDINATOR)),
    ASSESSMENT_RESOLVE("assessment.resolve", Access.of(UserRole.COORDINATOR)),

    // ---- Coordination --------------------------------------------------

    COORDINATION_ALLOCATE("coordination.allocate", Access.of(UserRole.COORDINATOR)),
    /**
     * A responder may update only a task dispatched to their own department.
     * The role check here is not enough on its own, so the handler makes a
     * second check against the session's department. Two users with the same
     * role can still be entitled to different records.
     */
    COORDINATION_UPDATE_STATUS("coordination.updateStatus",
            Access.of(UserRole.COORDINATOR, UserRole.RESPONDER)),
    COORDINATION_TASKS_FOR_DEPARTMENT("coordination.tasksForDepartment",
            Access.of(UserRole.RESPONDER, UserRole.COORDINATOR)),
    COORDINATION_REPORT_FOR_TASK("coordination.reportForTask",
            Access.of(UserRole.RESPONDER, UserRole.COORDINATOR)),
    COORDINATION_AVAILABLE_DEPARTMENTS("coordination.availableDepartments",
            Access.of(UserRole.COORDINATOR)),
    DEPARTMENT_FIND_ALL("department.findAll",
            Access.of(UserRole.COORDINATOR, UserRole.ADMINISTRATOR)),
    DEPARTMENT_SET_AVAILABILITY("department.setAvailability",
            Access.of(UserRole.ADMINISTRATOR, UserRole.COORDINATOR)),

    // ---- Alerts --------------------------------------------------------

    ALERT_ISSUE("alert.issue", Access.of(UserRole.COORDINATOR)),
    ALERT_FIND_ALL("alert.findAll", Access.of(UserRole.COORDINATOR, UserRole.ADMINISTRATOR)),

    // ---- Accounts and audit --------------------------------------------

    ACCOUNT_CREATE("account.create", Access.of(UserRole.ADMINISTRATOR)),
    ACCOUNT_DEACTIVATE("account.deactivate", Access.of(UserRole.ADMINISTRATOR)),
    ACCOUNT_ACTIVATE("account.activate", Access.of(UserRole.ADMINISTRATOR)),
    ACCOUNT_ASSIGN_ROLE("account.assignRole", Access.of(UserRole.ADMINISTRATOR)),
    ACCOUNT_FIND_ALL("account.findAll", Access.of(UserRole.ADMINISTRATOR)),
    AUDIT_FIND_ALL("audit.findAll", Access.of(UserRole.ADMINISTRATOR)),
    AUDIT_VERIFY_CHAIN("audit.verifyChain", Access.of(UserRole.ADMINISTRATOR)),

    // ---- Migration -----------------------------------------------------

    ADMIN_MIGRATE("admin.migrate", Access.of(UserRole.ADMINISTRATOR));

    /** Wire name to operation, built once. */
    private static final Map<String, Operation> BY_WIRE_NAME = buildIndex();

    private final String wireName;
    private final Access access;

    Operation(String wireName, Access access) {
        this.wireName = wireName;
        this.access = access;
    }

    /**
     * The name carried in the request's {@code operation} field.
     *
     * @return the wire name, such as report.submit
     */
    public String wireName() {
        return wireName;
    }

    /**
     * Whether this operation may be invoked without signing in.
     *
     * @return true when no session token is required
     */
    public boolean isPublic() {
        return access.isPublic();
    }

    /**
     * The roles permitted to invoke this operation.
     *
     * @return the permitted roles, which for a public operation is every role,
     *         since a public operation refuses nobody
     */
    public Set<UserRole> allowedRoles() {
        return access.roles();
    }

    /**
     * Whether a user holding this role may invoke this operation.
     *
     * @param role the role held by the session, or null when not signed in
     * @return true when the operation is permitted
     */
    public boolean permits(UserRole role) {
        if (access.isPublic()) {
            return true;
        }
        return role != null && access.roles().contains(role);
    }

    /**
     * Finds the operation a request named.
     *
     * @param wireName the value of the request's operation field
     * @return the operation, or an empty Optional when the name is not defined
     */
    public static Optional<Operation> fromWireName(String wireName) {
        if (wireName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_WIRE_NAME.get(wireName));
    }

    private static Map<String, Operation> buildIndex() {
        Map<String, Operation> index = new HashMap<>();
        for (Operation operation : values()) {
            Operation clash = index.put(operation.wireName, operation);
            if (clash != null) {
                // Two constants sharing a wire name would make one of them
                // unreachable, and which one won would depend on declaration
                // order. Failing at class load is better than a silently dead
                // operation.
                throw new IllegalStateException("Two operations share the wire name "
                        + operation.wireName + ": " + clash + " and " + operation);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    /**
     * Who may invoke an operation.
     *
     * Three cases are needed and they are genuinely different. A public
     * operation requires no session at all. An operation open to any signed-in
     * user requires a session but does not care which role it holds. The rest
     * name their roles.
     */
    private static final class Access {

        private static final Access PUBLIC = new Access(true, EnumSet.allOf(UserRole.class));
        private static final Access ANY_SIGNED_IN =
                new Access(false, EnumSet.allOf(UserRole.class));

        private final boolean open;
        private final Set<UserRole> roles;

        private Access(boolean open, Set<UserRole> roles) {
            this.open = open;
            this.roles = Collections.unmodifiableSet(roles);
        }

        static Access of(UserRole... roles) {
            // Built by adding to an empty EnumSet rather than with
            // EnumSet.copyOf, which cannot infer the element type from an
            // empty collection and throws rather than returning an empty set.
            EnumSet<UserRole> permitted = EnumSet.noneOf(UserRole.class);
            Collections.addAll(permitted, roles);
            return new Access(false, permitted);
        }

        boolean isPublic() {
            return open;
        }

        Set<UserRole> roles() {
            return roles;
        }
    }
}
