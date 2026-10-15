package com.cqu.drs.protocol;

/**
 * Whether a request was carried out.
 *
 * Only two values exist. A validation failure, a missing record and an access
 * refusal are all ERROR with a code, rather than separate statuses, so that a
 * client has one place to look to find out whether it got what it asked for.
 */
public enum ResponseStatus {

    /** The operation ran and the payload holds its result. */
    OK,

    /** The operation did not run. The error code says why. */
    ERROR
}
