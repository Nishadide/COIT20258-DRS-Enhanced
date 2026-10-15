package com.cqu.drs.protocol;

/**
 * A request that cannot be carried out, carrying the code to send back.
 *
 * Handlers throw this rather than returning an error object, so that a check
 * made deep inside a handler does not have to be threaded back up through
 * every return value. The dispatcher catches it and turns it into the
 * response.
 *
 * It extends RuntimeException deliberately. A checked exception would have to
 * be declared by every method between the check and the dispatcher, which in
 * practice leads to methods declaring it whether or not they throw it, and the
 * declaration then says nothing.
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * Creates a refusal.
     *
     * @param errorCode why the request is refused
     * @param message   text suitable for showing to the person at the keyboard
     */
    public ProtocolException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates a refusal caused by another failure.
     *
     * @param errorCode why the request is refused
     * @param message   text suitable for showing to the person at the keyboard
     * @param cause     the underlying failure, which is logged on the server
     *                  and never sent to the client
     */
    public ProtocolException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * A refusal for a payload that broke a domain rule.
     *
     * @param message the rule that was broken
     * @return the exception
     */
    public static ProtocolException validation(String message) {
        return new ProtocolException(ErrorCode.VALIDATION_FAILED, message);
    }

    /**
     * A refusal for a record that does not exist.
     *
     * @param what the kind of record
     * @param id   the identifier looked for
     * @return the exception
     */
    public static ProtocolException notFound(String what, String id) {
        return new ProtocolException(ErrorCode.NOT_FOUND,
                "No " + what + " was found for " + id + ".");
    }

    /**
     * A refusal because the session may not do this.
     *
     * @param message what was refused, without naming records the caller is
     *                not entitled to know exist
     * @return the exception
     */
    public static ProtocolException accessDenied(String message) {
        return new ProtocolException(ErrorCode.ACCESS_DENIED, message);
    }
}
