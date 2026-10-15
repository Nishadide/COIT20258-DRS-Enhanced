package com.cqu.drs.server;

import com.cqu.drs.migration.MigrationOperations;
import com.cqu.drs.service.AccountOperations;
import com.cqu.drs.service.AlertOperations;
import com.cqu.drs.service.AssessmentOperations;
import com.cqu.drs.service.AuditOperations;
import com.cqu.drs.service.AuthenticationService;
import com.cqu.drs.service.CoordinationOperations;
import com.cqu.drs.service.DepartmentOperations;
import com.cqu.drs.service.ReportOperations;

/**
 * The services the handlers are built from, gathered so that the one list of
 * registrations can be passed them all.
 *
 * <h2>Why this exists</h2>
 * {@link ServerMain#buildDispatcher} registers twenty-nine handlers, and
 * writing that list in the server and again in the tests would mean a handler
 * could be registered in one and forgotten in the other. The test that proves
 * every declared operation has a handler would then be proving it about the
 * test's own list, which is worth nothing.
 *
 * <p>So the list lives in one method, and this record is what that method
 * needs in order to be callable from a test as well as from the server. The
 * server passes services backed by MySQL; a test passes the same services
 * backed by JSON files in a temporary folder, and the registration list is
 * identical in both.
 *
 * <p>Authentication is the one field declared as a class rather than an
 * interface. The sign-in handlers need {@code authenticate}, which deliberately
 * remembers nobody, and {@code changePassword} and {@code recordLogout}, which
 * name the account they act on. None of those are on
 * {@code AuthenticationOperations}, because that interface describes what a
 * screen with one person at it needs, and a server has as many signed-in
 * people as it has clients.
 *
 * @param authentication checks credentials and records sign-ins
 * @param reports        lodging and finding disaster reports
 * @param assessments    judging how serious a disaster is
 * @param coordination   dispatching work and following it
 * @param alerts         issuing public warnings
 * @param accounts       managing staff accounts
 * @param audit          reading and verifying the audit trail
 * @param departments    the departments and their availability
 * @param migration      carrying the Assessment 2 data into the database
 */
public record ServerServices(AuthenticationService authentication,
                             ReportOperations reports,
                             AssessmentOperations assessments,
                             CoordinationOperations coordination,
                             AlertOperations alerts,
                             AccountOperations accounts,
                             AuditOperations audit,
                             DepartmentOperations departments,
                             MigrationOperations migration) {

    /**
     * Creates the set, refusing an incomplete one.
     *
     * A null service would not fail here. It would fail when somebody pressed
     * the control that needed it, as an INTERNAL_ERROR from a handler whose
     * collaborator was missing, and the cause would be a long way from the
     * symptom.
     */
    public ServerServices {
        if (authentication == null || reports == null || assessments == null
                || coordination == null || alerts == null || accounts == null
                || audit == null || departments == null || migration == null) {
            throw new IllegalArgumentException(
                    "Every service the handlers need must be supplied.");
        }
    }
}
