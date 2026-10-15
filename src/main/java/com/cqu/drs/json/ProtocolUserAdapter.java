package com.cqu.drs.json;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes a user onto the wire with the credential left behind.
 *
 * <h2>Why the store's adapter could not be used for this</h2>
 * {@link UserAdapter} serialises every field a user has, which is right for
 * the file it was written for: a store that dropped the password hash could
 * not sign anybody in after a restart.
 *
 * <p>It is wrong for the protocol. A user travels to the client on the login
 * response and in the list of accounts an administrator sees, and with that
 * adapter each one carried its SHA-256 password hash with it. Anyone watching
 * the connection, or any modified client, would collect the hash of every
 * account in the system from one request. Hashes are not passwords, but they
 * are what the server compares against, and a stolen hash is the starting
 * point of an offline guessing attack that nothing on the server can see or
 * rate limit.
 *
 * <p>This was found by asking what the codec actually emitted rather than by
 * reading it, and it emitted the hash in all three forms a user is sent in.
 *
 * <h2>How a field is judged</h2>
 * The rule is applied to the field's name rather than to a list of the fields
 * that exist today, because two more members of the team are adding features
 * to this model and a list would be correct only until the next one is added.
 * A field is dropped when
 *
 * <ul>
 *   <li>its name is one of the names a credential is known by here, or</li>
 *   <li>its name suggests a secret, meaning it contains password, secret,
 *       credential, token or salt, or ends in hash.</li>
 * </ul>
 *
 * <p>A boolean is exempt from the second rule, because a true or false cannot
 * be a credential and the model has a flag called {@code mustChangePassword}
 * that the client needs in order to prompt for a new password. Dropping it
 * would stop that prompt appearing, which is a fault the name test would have
 * caused and the exemption prevents.
 *
 * <h2>What the client gets instead</h2>
 * A user with no hash at all. That is correct rather than merely safe: a
 * client has no business checking a password, and one that held the hash could
 * be modified to accept any password it liked and then tell its own screens
 * the sign-in succeeded. The decision belongs to the server, which is why the
 * client is given nothing to decide it with.
 */
public final class ProtocolUserAdapter
        implements JsonSerializer<User>, JsonDeserializer<User> {

    private static final String ROLE_FIELD = "role";

    /** Names a credential is known by in this model and its stores. */
    private static final Set<String> CREDENTIAL_FIELDS = Set.of(
            "passwordhash", "password", "plainpassword", "salt",
            "signingkey", "sessiontoken");

    /** Fragments that mark a field as holding something secret. */
    private static final List<String> SECRET_FRAGMENTS = List.of(
            "password", "secret", "credential", "token", "salt");

    /**
     * Reflection only, and deliberately without any User adapter registered.
     *
     * This adapter is registered on the protocol's Gson as a type hierarchy
     * adapter, so it is consulted for User and for all three of its
     * subclasses. Asking that same Gson to serialise the concrete class would
     * therefore call this adapter again and go round for ever. This instance
     * is the way out: it knows nothing about users and simply reflects over
     * their fields.
     *
     * <p>The date adapter is registered on it so that a timestamp added to a
     * user by a later feature is written in the one format the rest of the
     * system uses, rather than in Gson's default.
     */
    private static final Gson PLAIN = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /**
     * Writes the user, less anything that is a credential.
     *
     * The concrete class is serialised rather than the declared one, so a
     * responder keeps the department identifier its screens need.
     *
     * @param source  the user
     * @param type    the declared type, which is not used
     * @param context the serialisation context, which is not used because
     *                re-entering it would call this adapter again
     * @return the user as JSON
     */
    @Override
    public JsonElement serialize(User source, Type type, JsonSerializationContext context) {
        if (source == null) {
            return null;
        }
        JsonElement written = PLAIN.toJsonTree(source, source.getClass());
        if (!written.isJsonObject()) {
            return written;
        }
        return withoutCredentials(written.getAsJsonObject());
    }

    /**
     * Reads a user back, choosing the subclass from the role.
     *
     * The user that comes out has no password hash, because none was sent. It
     * is a record of who somebody is, not a means of checking them.
     *
     * @param json    the JSON received
     * @param type    the declared type, which is not used
     * @param context the context, which is not used, for the reason given on
     *                {@link #PLAIN}
     * @return the user
     * @throws JsonParseException when the record carries no role, since
     *         without it there is no way to know which subclass to build
     */
    @Override
    public User deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement roleElement = object.get(ROLE_FIELD);
        if (roleElement == null || roleElement.isJsonNull()) {
            throw new JsonParseException(
                    "A user arrived with no role, so which kind of user it is "
                            + "cannot be determined.");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(roleElement.getAsString());
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("A user arrived holding the role \""
                    + roleElement.getAsString() + "\", which is not a role.", e);
        }

        return switch (role) {
            case COORDINATOR -> PLAIN.fromJson(object, EmergencyCoordinator.class);
            case RESPONDER -> PLAIN.fromJson(object, DepartmentResponder.class);
            case ADMINISTRATOR -> PLAIN.fromJson(object, SystemAdministrator.class);
        };
    }

    /**
     * Removes the fields that must not leave the server.
     *
     * The names are collected before any is removed. Removing while iterating
     * the entry set would modify the collection being walked.
     *
     * @param written the user as reflection wrote it
     * @return the same object with the credentials gone
     */
    private static JsonObject withoutCredentials(JsonObject written) {
        List<String> drop = new ArrayList<>();
        for (String name : written.keySet()) {
            if (isCredential(name, written.get(name))) {
                drop.add(name);
            }
        }
        for (String name : drop) {
            written.remove(name);
        }
        return written;
    }

    /**
     * Whether a field holds something that must stay on the server.
     *
     * @param name  the field name
     * @param value the value written for it
     * @return true when the field must be dropped
     */
    private static boolean isCredential(String name, JsonElement value) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (CREDENTIAL_FIELDS.contains(lower)) {
            return true;
        }
        if (isBoolean(value)) {
            // A flag, not a secret. mustChangePassword is the one the client
            // needs, and the name test below would otherwise take it.
            return false;
        }
        if (lower.endsWith("hash")) {
            return true;
        }
        for (String fragment : SECRET_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isBoolean();
    }
}
