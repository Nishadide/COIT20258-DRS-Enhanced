package com.cqu.drs.client;

import com.cqu.drs.protocol.ErrorCode;
import com.cqu.drs.protocol.ProtocolCodec;
import com.cqu.drs.protocol.ProtocolException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Reads domain objects out of a response payload.
 *
 * Every remote service needs the same four readings, and doing them in one
 * place keeps two things consistent that would otherwise drift. Objects are
 * always built with the protocol's own Gson, so timestamps and the user
 * hierarchy are handled the same way the server wrote them; and a payload that
 * does not carry what it should raises a refusal naming the field rather than
 * a NullPointerException two screens away from the cause.
 *
 * <p>A response is not trusted simply because it came from the server. The
 * client has no way of knowing it was the server that answered, which is one
 * of the reasons the protocol signs requests, and a reply that is the wrong
 * shape is reported as such instead of being read past.
 */
final class Results {

    /** Not instantiable: this class holds static readers only. */
    private Results() {
    }

    /**
     * One object the operation was asked for and must return.
     *
     * @param payload the response payload
     * @param field   the field holding it
     * @param type    the class to build
     * @param <T>     the type built
     * @return the object
     * @throws ProtocolException when the field is absent
     */
    static <T> T one(JsonObject payload, String field, Class<T> type) {
        T value = orNull(payload, field, type);
        if (value == null) {
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "The server's reply carried no " + field + ".");
        }
        return value;
    }

    /**
     * One object that the operation may legitimately not find.
     *
     * @param payload the response payload
     * @param field   the field holding it
     * @param type    the class to build
     * @param <T>     the type built
     * @return the object, or an empty Optional
     */
    static <T> Optional<T> optional(JsonObject payload, String field, Class<T> type) {
        return Optional.ofNullable(orNull(payload, field, type));
    }

    /**
     * A list of objects. An absent field reads as an empty list, because an
     * operation that found nothing and an operation that returned nothing are
     * the same thing to a screen showing a table.
     *
     * @param payload the response payload
     * @param field   the field holding the list
     * @param type    the class to build for each element
     * @param <T>     the type built
     * @return the objects, never null
     */
    static <T> List<T> list(JsonObject payload, String field, Class<T> type) {
        JsonElement element = payload == null ? null : payload.get(field);
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }
        if (!element.isJsonArray()) {
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "The server's reply carried a single " + field
                            + " where a list was expected.");
        }
        JsonArray array = element.getAsJsonArray();
        List<T> values = new ArrayList<>(array.size());
        for (JsonElement each : array) {
            if (each != null && !each.isJsonNull()) {
                values.add(ProtocolCodec.gson().fromJson(each, type));
            }
        }
        return values;
    }

    /**
     * A true or false the operation reported, such as whether an account was
     * already active.
     *
     * @param payload the response payload
     * @param field   the field holding it
     * @return the value, or false when absent
     */
    static boolean flag(JsonObject payload, String field) {
        JsonElement element = payload == null ? null : payload.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return false;
        }
        return element.getAsBoolean();
    }

    /**
     * A string the operation returned, such as a reference number.
     *
     * @param payload the response payload
     * @param field   the field holding it
     * @return the value
     * @throws ProtocolException when the field is absent
     */
    static String text(JsonObject payload, String field) {
        JsonElement element = payload == null ? null : payload.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            throw new ProtocolException(ErrorCode.MALFORMED_REQUEST,
                    "The server's reply carried no " + field + ".");
        }
        return element.getAsString();
    }

    private static <T> T orNull(JsonObject payload, String field, Class<T> type) {
        JsonElement element = payload == null ? null : payload.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return ProtocolCodec.gson().fromJson(element, type);
    }
}
