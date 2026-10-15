package com.cqu.drs.client;

import com.cqu.drs.model.DisasterReport;
import com.cqu.drs.model.DisasterType;
import com.cqu.drs.model.Reporter;
import com.cqu.drs.model.Severity;
import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.ProtocolException;
import com.cqu.drs.repository.DisasterReportRepository;
import com.cqu.drs.repository.InMemoryRequestLogRepository;
import com.cqu.drs.repository.JsonAuditRepository;
import com.cqu.drs.repository.JsonDataStore;
import com.cqu.drs.repository.JsonDisasterReportRepository;
import com.cqu.drs.server.DrsServer;
import com.cqu.drs.server.RequestDispatcher;
import com.cqu.drs.server.SessionRegistry;
import com.cqu.drs.server.handler.FindReportByReferenceHandler;
import com.cqu.drs.server.handler.SubmitReportHandler;
import com.cqu.drs.service.AuditService;
import com.cqu.drs.service.ReportOperations;
import com.cqu.drs.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T197 to T208: a report travelling from the client, over a socket, to
 * the server and back.
 *
 * These run a real server on a real port and a real client against it. The
 * only thing standing in for production is the storage: the server's
 * repository writes to a temporary folder rather than MySQL, so these run on
 * any machine with no database installed.
 *
 * <p>That substitution is deliberate and is the reason this architecture was
 * worth building. The server is handed a {@link DisasterReportRepository} and
 * does not know which one. Swapping the storage changes nothing above it,
 * which is exactly the claim the Assessment 2 report made about its own
 * design, now being used rather than asserted.
 *
 * <p>The MySQL half of the path is proved separately, in
 * {@code WalkingSkeletonTest}, which needs a database and skips without one.
 */
@DisplayName("Reports over the protocol")
class RemoteReportServiceTest {

    private DrsServer server;
    private DrsClient client;
    private RemoteReportService remote;
    private DisasterReportRepository serverRepository;

    @BeforeEach
    void startServerAndClient(@TempDir Path serverData) throws IOException {
        JsonDataStore store = new JsonDataStore(serverData);
        serverRepository = new JsonDisasterReportRepository(store);

        AuditService auditService = new AuditService(new JsonAuditRepository(store));
        ReportOperations reportService = new ReportService(serverRepository, auditService);

        RequestDispatcher dispatcher =
                new RequestDispatcher(new SessionRegistry(), new InMemoryRequestLogRepository())
                        .register(new SubmitReportHandler(reportService))
                        .register(new FindReportByReferenceHandler(reportService));

        server = new DrsServer(0, 4, dispatcher);
        server.start();

        client = new DrsClient("127.0.0.1", server.getPort());
        remote = new RemoteReportService(client);
    }

    @AfterEach
    void stopServerAndClient() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    private String submitFire() {
        return remote.submitReport(DisasterType.FIRE, "12 Quay Street, Rockhampton",
                -23.377723, 150.510742, Severity.HIGH, "Smoke from the top floor.",
                new Reporter("RP-clientside", "A. Reporter", "0412345678"));
    }

    // ---- tests ---------------------------------------------------------

    @Test
    @DisplayName("T197: a report lodged on the client is stored on the server")
    void reportTravelsToTheServer() {
        String reference = submitFire();

        assertEquals("DR-0001", reference,
                "The server allocates the reference and returns it.");

        // Read straight from the server's own repository, not back through the
        // protocol, so this checks the report really was stored rather than
        // that the server can echo.
        DisasterReport stored = serverRepository.findByReference("DR-0001").orElseThrow();
        assertEquals(DisasterType.FIRE, stored.getType());
        assertEquals("12 Quay Street, Rockhampton", stored.getLocation().getAddress());
        assertEquals(Severity.HIGH, stored.getReportedSeverity());
        assertEquals("Smoke from the top floor.", stored.getDescription());
    }

    @Test
    @DisplayName("T198: the report comes back whole when looked up by reference")
    void reportComesBackByReference() {
        String reference = submitFire();

        DisasterReport read = remote.findByReference(reference).orElseThrow();

        assertEquals(reference, read.getReferenceNumber());
        assertEquals(DisasterType.FIRE, read.getType());
        assertEquals(Severity.HIGH, read.getReportedSeverity());
        assertEquals("A. Reporter", read.getReporter().getName());
    }

    @Test
    @DisplayName("T199: coordinates survive the round trip")
    void coordinatesSurviveTheRoundTrip() {
        DisasterReport read = remote.findByReference(submitFire()).orElseThrow();

        assertEquals(-23.377723, read.getLocation().getLatitude(), 0.0000001);
        assertEquals(150.510742, read.getLocation().getLongitude(), 0.0000001);
        assertTrue(read.getLocation().hasCoordinates());
    }

    @Test
    @DisplayName("T200: the time the report was lodged survives the round trip")
    void timestampSurvivesTheRoundTrip() {
        // Serialised by the protocol's Gson, which carries the LocalDateTime
        // adapter written for Assessment 2. If that adapter were missing from
        // the protocol's configuration the timestamp would arrive as an empty
        // object and this would fail.
        String reference = submitFire();

        DisasterReport onTheServer = serverRepository.findByReference(reference).orElseThrow();
        DisasterReport overTheWire = remote.findByReference(reference).orElseThrow();

        assertEquals(onTheServer.getReportedAt(), overTheWire.getReportedAt());
    }

    @Test
    @DisplayName("T201: an anonymous report is accepted and stays anonymous")
    void anonymousReportIsAccepted() {
        String reference = remote.submitReport(DisasterType.FLOOD, "Riverside Drive",
                null, null, Severity.MEDIUM, null, null);

        DisasterReport read = remote.findByReference(reference).orElseThrow();

        assertTrue(read.isAnonymous());
        assertFalse(read.getLocation().hasCoordinates());
    }

    @Test
    @DisplayName("T202: the reporter's identifier is allocated by the server, not the client")
    void serverAllocatesTheReporterIdentifier() {
        // The client sent a reporter whose identifier it made up. Identifiers
        // belong to the tier that stores them: one chosen by a caller is one
        // the caller can collide with or reuse, by accident or deliberately.
        String reference = submitFire();

        Reporter stored = serverRepository.findByReference(reference)
                .orElseThrow().getReporter();

        assertNotEquals("RP-clientside", stored.getReporterId());
        assertTrue(stored.getReporterId().startsWith("RP-"),
                "The server allocates the identifier: " + stored.getReporterId());
        assertEquals("A. Reporter", stored.getName(),
                "The details the client sent are kept; only the identifier is "
                        + "the server's to decide.");
    }

    @Test
    @DisplayName("T203: a reference nobody holds returns an empty Optional, not a failure")
    void unknownReferenceIsNotAFailure() {
        // Someone mistyping their reference is ordinary, and the screen
        // already knows how to say so. Turning it into an exception would
        // make the client handle a normal outcome as a fault.
        assertTrue(remote.findByReference("DR-9999").isEmpty());
        assertTrue(remote.findByReference("").isEmpty());
        assertTrue(remote.findByReference(null).isEmpty());
    }

    @Test
    @DisplayName("T204: a reference is matched whatever case it is typed in")
    void referenceIsCaseInsensitive() {
        String reference = submitFire();
        assertTrue(remote.findByReference(reference.toLowerCase()).isPresent(),
                "The screen used to upper-case this. Doing it on the server "
                        + "means every client behaves the same way.");
    }

    @Test
    @DisplayName("T205: a report the server refuses is reported as a validation failure")
    void serverValidationReachesTheCaller() {
        // The client checks the same rules before sending, so this is the
        // server's own check being exercised: a request built without the
        // client's help still cannot store a report with no address.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> remote.submitReport(DisasterType.FIRE, "   ", null, null,
                        Severity.HIGH, null, null));

        assertTrue(refused.getMessage().contains("location"),
                "The reason must reach the person: " + refused.getMessage());
    }

    @Test
    @DisplayName("T206: the client checks the rules itself, without asking the server")
    void clientValidatesLocally() {
        // Stopping the server first proves no request was sent. Waiting for a
        // round trip to be told a field is empty would make the form feel
        // broken.
        server.stop();

        List<String> faults = remote.validate(null, "", null, null, null, null, null);

        assertTrue(faults.size() >= 3,
                "Type, location and severity are all missing: " + faults);
    }

    @Test
    @DisplayName("T207: a server that is not running is reported as unreachable")
    void serverDownIsDistinctFromRefusal() {
        server.stop();
        client.close();

        // Not an IllegalArgumentException. Telling someone their report was
        // invalid when nobody was listening would send them to fix a form that
        // is already correct.
        assertThrows(ServerUnavailableException.class, this::submitFire);
    }

    @Test
    @DisplayName("T208: several operations share one connection")
    void oneConnectionServesManyOperations() {
        submitFire();
        remote.submitReport(DisasterType.FLOOD, "Riverside Drive", null, null,
                Severity.LOW, null, null);
        remote.findByReference("DR-0001");
        remote.findByReference("DR-0002");

        assertEquals(1, server.getConnectionsAccepted(),
                "The session holds one connection open. Four operations must "
                        + "not have opened four connections.");
    }

    @Test
    @DisplayName("References are allocated in order as reports arrive")
    void referencesAreAllocatedInOrder() {
        assertEquals("DR-0001", submitFire());
        assertEquals("DR-0002", submitFire());
        assertEquals("DR-0003", submitFire());
    }

    @Test
    @DisplayName("An operation needing a session is refused when nobody is signed in")
    void operationNeedingASessionIsRefused() {
        // findAll is for coordinators. Reaching it without signing in must be
        // refused by the server, not merely hidden by the client.
        ProtocolException refused = assertThrows(ProtocolException.class,
                () -> remote.findAll());

        assertEquals(ErrorCode.NOT_AUTHENTICATED, refused.getErrorCode());
    }

    @Test
    @DisplayName("A remote service cannot be built without a client")
    void remoteServiceRequiresAClient() {
        assertThrows(IllegalArgumentException.class, () -> new RemoteReportService(null));
    }

    @Test
    @DisplayName("Lodging a report through the client leaves it findable through the client")
    void endToEndThroughTheProtocolOnly() {
        // The whole path, using nothing but the client: lodge, then look up.
        // This is what the screens actually do.
        String reference = remote.submitReport(DisasterType.EARTHQUAKE, "Civic Square",
                null, null, Severity.SEVERE, "Building shifted.", null);

        Optional<DisasterReport> found = remote.findByReference(reference);

        assertTrue(found.isPresent());
        assertEquals(DisasterType.EARTHQUAKE, found.get().getType());
        assertEquals("Building shifted.", found.get().getDescription());
    }
}
