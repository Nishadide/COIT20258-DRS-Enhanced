package com.cqu.drs.protocol;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Signs requests and checks signatures, which is how the non-repudiation
 * requirement is met at the level of an individual request.
 *
 * <p>At login the server issues a signing key for the session and sends it
 * once, on the login response only. The client signs every later request with
 * it and the server recomputes the signature and compares. A request carrying
 * a valid signature could only have been produced by something holding a key
 * issued to that session, so the actor cannot credibly claim not to have sent
 * it.
 *
 * <p>The signature covers the request identifier, the operation, the time the
 * client sent it and the payload. Covering the identifier and the time is what
 * stops a captured request being replayed: a second use of the same identifier
 * is refused, and changing the identifier to avoid that would invalidate the
 * signature, which the sender cannot recompute without the key.
 *
 * <p>HMAC is used rather than a plain hash of the key and the message
 * concatenated. A plain hash of that form is vulnerable to length extension,
 * where a valid signature for a longer message can be derived from a valid
 * signature for a shorter one without knowing the key. HMAC's nested
 * construction exists to prevent exactly that.
 */
public final class RequestSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 32 bytes, matching the 256 bit output of SHA-256. */
    private static final int KEY_LENGTH_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Not instantiable: this class holds static methods only. */
    private RequestSigner() {
    }

    /**
     * Generates a signing key for a new session.
     *
     * SecureRandom is used rather than Random. Random is a linear congruential
     * generator whose entire future output can be predicted from a couple of
     * observed values, which for a key would mean anyone who saw one session's
     * key could forge the next.
     *
     * @return the key, base64 encoded for carrying in a JSON message
     */
    public static String newSigningKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * Computes the signature for a request.
     *
     * @param signingKey the session's key, base64 encoded
     * @param request    the request to sign
     * @return the signature as lowercase hexadecimal
     */
    public static String sign(String signingKey, Request request) {
        return hmacHex(signingKey, canonicalForm(request));
    }

    /**
     * Checks a request's signature against its contents.
     *
     * @param signingKey the session's key, base64 encoded
     * @param request    the request as received
     * @return true when the signature matches
     */
    public static boolean verify(String signingKey, Request request) {
        if (signingKey == null || request == null || request.getSignature() == null) {
            return false;
        }
        String expected = sign(signingKey, request);

        // Compared with MessageDigest.isEqual, which takes the same time
        // whatever the inputs. String.equals returns as soon as it finds a
        // differing character, so the time it takes reveals how many leading
        // characters were correct, and a signature can be recovered one
        // character at a time by measuring it.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                request.getSignature().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The exact bytes a signature is computed over.
     *
     * Both sides build this the same way from the same four parts, separated
     * by newlines so that no two different requests can produce the same
     * string by running one field into the next.
     *
     * @param request the request
     * @return the canonical form
     */
    public static String canonicalForm(Request request) {
        return request.getRequestId()
                + "\n" + request.getOperation()
                + "\n" + (request.getSentAt() == null
                          ? "" : TIMESTAMP.format(request.getSentAt()))
                + "\n" + ProtocolCodec.canonicalPayload(request.getPayload());
    }

    private static String hmacHex(String base64Key, String message) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // HmacSHA256 is required of every Java implementation, so this
            // cannot happen on a working JVM.
            throw new IllegalStateException(ALGORITHM + " is not available.", e);
        } catch (InvalidKeyException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "The signing key is not valid base64 of a usable key.", e);
        }
    }
}
