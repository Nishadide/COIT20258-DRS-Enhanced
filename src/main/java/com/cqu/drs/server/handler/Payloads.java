package com.cqu.drs.server.handler;

import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads values out of a request payload, and refuses the request when they are
 * not there or not what they should be.
 *
 * Every handler needs the same half dozen readers, and a handler that reads a
 * field without checking it is the one that turns a malformed request into a
 * NullPointerException and an INTERNAL_ERROR. Doing it in one place means the
 * refusal is always a VALIDATION_FAILED naming the field, which tells the
 * person at the other end something they can act on.
 *
 * <p>Nothing here trusts the payload. It arrives from a socket and may have
 * been written by anything.
 */
public final class Payloads {

    /** Not instantiable: this class holds static readers only. */
    private Payloads() {
    }

    /**
     * A field that must be present and not blank.
     *
     * @param payload the request payload
     * @param field   the field name
     * @return the value, trimmed
     * @throws ProtocolException when the field is absent or blank
     */
    public static String requireString(JsonObject payload, String field) {
        String value = optionalString(payload, field);
        if (value == null) {
            throw ProtocolException.validation(field + " is required.");
        }
        return value;
    }

    /**
     * A field that may be absent.
     *
     * @param payload the request payload
     * @param field   the field name
     * @return the value trimmed, or null when absent or blank
     */
    public static String optionalString(JsonObject payload, String field) {
        JsonElement element = payload.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * A number that may be absent.
     *
     * @param payload the request payload
     * @param field   the field name
     * @return the value, or null when absent
     * @throws ProtocolException when the value is present but is not a number
     */
    public static Double optionalDouble(JsonObject payload, String field) {
        JsonElement element = payload.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException e) {
            throw ProtocolException.validation(field + " must be a number.");
        }
    }

    /**
     * A true or false value.
     *
     * @param payload the request payload
     * @param field   the field name
     * @return the value
     * @throws ProtocolException when the field is absent or is not a boolean
     */
    public static boolean requireBoolean(JsonObject payload, String field) {
        JsonElement element = payload.get(field);
        if (element == null || element.isJsonNull()) {
            throw ProtocolException.validation(field + " is required.");
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            throw ProtocolException.validation(field + " must be true or false.");
        }
    }

    /**
     * A value that must name one of an enumeration's constants.
     *
     * @param payload the request payload
     * @param field   the field name
     * @param type    the enumeration
     * @param <E>     the enumeration type
     * @return the constant
     * @throws ProtocolException when the field is absent or names nothing
     */
    public static <E extends Enum<E>> E requireEnum(JsonObject payload, String field,
                                                    Class<E> type) {
        String value = requireString(payload, field);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(
                    "\"" + value + "\" is not a recognised " + field + ".");
        }
    }

    /**
     * A value naming an enumeration constant, which may be absent.
     *
     * @param payload the request payload
     * @param field   the field name
     * @param type    the enumeration
     * @param <E>     the enumeration type
     * @return the constant, or null when absent
     */
    public static <E extends Enum<E>> E optionalEnum(JsonObject payload, String field,
                                                     Class<E> type) {
        String value = optionalString(payload, field);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ProtocolException.validation(
                    "\"" + value + "\" is not a recognised " + field + ".");
        }
    }

    /**
     * A list of strings, such as the department identifiers to dispatch to.
     *
     * @param payload the request payload
     * @param field   the field name
     * @return the values, never null and never empty
     * @throws ProtocolException when the field is absent, is not a list, or is
     *         empty
     */
    public static List<String> requireStrings(JsonObject payload, String field) {
        JsonElement element = payload.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw ProtocolException.validation(field + " must be a list.");
        }
        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement each : array) {
            if (each != null && !each.isJsonNull()) {
                String value = each.getAsString().trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        if (values.isEmpty()) {
            throw ProtocolException.validation(field + " must name at least one value.");
        }
        return values;
    }

    /**
     * Wraps a result as a payload under one name.
     *
     * @param name  the field name the client will read
     * @param value the value, serialised by the protocol's Gson so that
     *              timestamps and the user hierarchy are handled
     * @return the payload
     */
    public static JsonObject of(String name, Object value) {
        JsonObject payload = new JsonObject();
        payload.add(name, ProtocolCodec.gson().toJsonTree(value));
        return payload;
    }

    /**
     * An empty acknowledgement, for an operation that returns nothing.
     *
     * @return a payload carrying only that the operation was done
     */
    public static JsonObject acknowledged() {
        JsonObject payload = new JsonObject();
        payload.addProperty("acknowledged", true);
        return payload;
    }
}
