package com.cqu.drs.server;

import com.cqu.drs.migration.DataMigrationService;
import com.cqu.drs.migration.JsonMigrationSource;
import com.cqu.drs.migration.MigrationLedger;
import com.cqu.drs.protocol.Operation;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.cqu.drs.repository.JsonAlertRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonDepartmentRepository;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import com.cqu.drs.repository.JsonUserRepository;
import com.cqu.drs.service.AccountService;
import com.cqu.drs.service.AlertService;
import com.cqu.drs.service.AssessmentService;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.AuthenticationService;
import com.cqu.drs.service.CoordinationService;
import com.cqu.drs.service.DepartmentService;
import com.cqu.drs.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T317 to T320: every operation the protocol declares can be carried
 * out.
 *
 * <h2>The fault this is here to prevent</h2>
 * The protocol declares the operations all three members agreed, and the
 * handlers are written one at a time behind them. An operation with no handler
 * is refused with INTERNAL_ERROR, which is honest but is discovered by a user
 * pressing a control, usually during a demonstration.
 *
 * <p>This asks the question the other way round: the server's own
 * registration list is built here, over JSON repositories, and asked whether
 * anything is missing. It uses {@link ServerMain#buildDispatcher}, not a list
 * of its own, because a test with its own list would be checking its own
 * memory rather than the server's.
 */
@DisplayName("Operation coverage")
class DispatcherCoverageTest {

    private RequestDispatcher buildRealDispatcher(Path folder) {
        JsonDataStore store = new JsonDataStore(folder);
        JsonDisasterReportRepository reports = new JsonDisasterReportRepository(store);
        JsonUserRepository accounts = new JsonUserRepository(store);
        JsonAlertRepository alerts = new JsonAlertRepository(store);
        JsonAuditRepository auditTrail = new JsonAuditRepository(store);
        JsonDepartmentRepository departments = new JsonDepartmentRepository(store);

        AuditService audit = new AuditService(auditTrail);

        return ServerMain.buildDispatcher(new SessionRegistry(),
                new InMemoryRequestLogRepository(),
                new ServerServices(
                        new AuthenticationService(accounts, audit),
                        new ReportService(reports, audit),
                        new AssessmentService(reports, audit),
                        new CoordinationService(reports, departments, audit),
                        new AlertService(reports, alerts, audit),
                        new AccountService(accounts, audit),
                        audit,
                        new DepartmentService(departments, audit),
                        new DataMigrationService(new JsonMigrationSource(folder),
                                departments, accounts, reports, alerts, auditTrail,
                                new MapLedger())));
    }

    @Test
    @DisplayName("T317: no declared operation is left without a handler")
    void everyOperationHasAHandler(@TempDir Path folder) {
        List<Operation> missing = buildRealDispatcher(folder).unhandledOperations();

        assertTrue(missing.isEmpty(),
                "These operations are declared in the protocol and cannot be "
                        + "carried out: " + missing);
    }

    @Test
    @DisplayName("T318: the handler count matches the number of operations")
    void handlerCountMatchesOperationCount(@TempDir Path folder) {
        // Said separately from T317 because the two fail for different
        // reasons. A handler registered for an operation that no longer exists
        // would not show up as missing, and this is where the arithmetic stops
        // adding up.
        assertEquals(Operation.values().length,
                buildRealDispatcher(folder).handlerCount());
    }

    @Test
    @DisplayName("T319: every operation is reachable by the name it is sent under")
    void everyOperationIsReachableByWireName(@TempDir Path folder) {
        RequestDispatcher dispatcher = buildRealDispatcher(folder);

        for (Operation operation : Operation.values()) {
            Operation found = Operation.fromWireName(operation.wireName()).orElseThrow(
                    () -> new AssertionError("The wire name " + operation.wireName()
                            + " does not resolve to an operation."));
            assertTrue(dispatcher.handles(found),
                    "Nothing handles " + operation.wireName() + ".");
        }
    }

    @Test
    @DisplayName("T320: a service set with a gap in it is refused at construction")
    void incompleteServiceSetIsRefused() {
        // Rather than failing later as an INTERNAL_ERROR from whichever
        // handler happened to need the missing one.
        assertThrows(IllegalArgumentException.class, () -> new ServerServices(
                null, null, null, null, null, null, null, null, null));
    }

    /** A ledger in a map; this test never runs a migration. */
    private static final class MapLedger implements MigrationLedger {

        private final Map<String, String> entries = new HashMap<>();

        @Override
        public boolean hasRun(String item) {
            return entries.containsKey(item);
        }

        @Override
        public Optional<String> detailOf(String item) {
            return Optional.ofNullable(entries.get(item));
        }

        @Override
        public void record(String item, String detail) {
            entries.put(item, detail);
        }
    }
}
