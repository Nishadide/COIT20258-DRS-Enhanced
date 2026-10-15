package com.cqu.drs.protocol;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T140 to T148: request signing, which is how non-repudiation is met for
 * an individual request.
 *
 * The tests that matter most are the ones showing a signature stops being
 * valid when any part of the request changes. A signature that survived
 * tampering would prove nothing, and the whole non-repudiation claim rests on
 * it not surviving.
 */
@DisplayName("RequestSigner")
class RequestSignerTest {

    private static final String KEY = RequestSigner.newSigningKey();

    private Request signedRequest(String key) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reference", "DR-0001");
        payload.addProperty("severity", "HIGH");

        Request request = new Request(Operation.ASSESSMENT_ASSESS.wireName(), payload);
        request.setSignature(RequestSigner.sign(key, request));
        return request;
    }

    @Test
    @DisplayName("T140: a request signed with a key verifies against that key")
    void signedRequestVerifies() {
        assertTrue(RequestSigner.verify(KEY, signedRequest(KEY)));
    }

    @Test
    @DisplayName("T141: altering the payload invalidates the signature")
    void alteredPayloadFailsVerification() {
        Request request = signedRequest(KEY);

        // The realistic attack: a captured request for a HIGH assessment
        // changed to SEVERE, which would change which disasters are
        // dispatched first.
        request.getPayload().addProperty("severity", "SEVERE");

        assertFalse(RequestSigner.verify(KEY, request),
                "A payload changed after signing must not verify.");
    }

    @Test
    @DisplayName("T142: altering the operation invalidates the signature")
    void alteredOperationFailsVerification() {
        Request request = signedRequest(KEY);
        request.setOperation(Operation.ASSESSMENT_RESOLVE.wireName());

        assertFalse(RequestSigner.verify(KEY, request));
    }

    @Test
    @DisplayName("T143: altering the request identifier invalidates the signature")
    void alteredRequestIdFailsVerification() {
        Request request = signedRequest(KEY);

        // This is what stops a replay being worked around. A captured request
        // is refused because its identifier has been used, and changing the
        // identifier to get past that breaks the signature, which the captor
        // cannot recompute without the key.
        request.setRequestId("a-different-identifier");

        assertFalse(RequestSigner.verify(KEY, request));
    }

    @Test
    @DisplayName("T144: altering the time sent invalidates the signature")
    void alteredTimestampFailsVerification() {
        Request request = signedRequest(KEY);
        request.setSentAt(LocalDateTime.now().minusHours(3));

        assertFalse(RequestSigner.verify(KEY, request));
    }

    @Test
    @DisplayName("T145: a signature from one session does not verify under another key")
    void signatureFromAnotherKeyFails() {
        Request request = signedRequest(KEY);
        String otherSessionKey = RequestSigner.newSigningKey();

        assertFalse(RequestSigner.verify(otherSessionKey, request),
                "A signature must identify the session that produced it.");
    }

    @Test
    @DisplayName("T146: a request with no signature does not verify")
    void unsignedRequestFails() {
        Request request = new Request(Operation.ASSESSMENT_ASSESS.wireName(), new JsonObject());
        assertFalse(RequestSigner.verify(KEY, request));
    }

    @Test
    @DisplayName("T147: verification refuses rather than fails when inputs are absent")
    void verifyHandlesMissingInputs() {
        // The dispatcher calls this on whatever arrives, including a request
        // built by something that is not our client. It must return false
        // rather than throw, or a crafted request would take down the thread
        // serving it.
        assertFalse(RequestSigner.verify(null, signedRequest(KEY)));
        assertFalse(RequestSigner.verify(KEY, null));
    }

    @Test
    @DisplayName("T148: each session is issued a different 32 byte key")
    void keysAreDistinctAndFullLength() {
        String first = RequestSigner.newSigningKey();
        String second = RequestSigner.newSigningKey();

        assertNotEquals(first, second, "Two sessions sharing a key could sign "
                + "for one another.");
        assertEquals(32, Base64.getDecoder().decode(first).length,
                "The key must match the 256 bit output of SHA-256.");
    }

    @Test
    @DisplayName("The canonical form separates its parts so two requests cannot collide")
    void canonicalFormSeparatesItsParts() {
        // Without separators, a request with id "ab" and operation "cd" and
        // one with id "abc" and operation "d" would produce the same string
        // and therefore the same signature.
        Request first = new Request("cd", new JsonObject());
        first.setRequestId("ab");
        first.setSentAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        Request second = new Request("d", new JsonObject());
        second.setRequestId("abc");
        second.setSentAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertNotEquals(RequestSigner.canonicalForm(first),
                RequestSigner.canonicalForm(second));
    }

    @Test
    @DisplayName("Signing the same request twice gives the same signature")
    void signingIsDeterministic() {
        // The server recomputes the signature rather than storing it, so the
        // computation has to be repeatable.
        Request request = signedRequest(KEY);
        assertEquals(request.getSignature(), RequestSigner.sign(KEY, request));
    }
}
