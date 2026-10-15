package com.cqu.drs.json;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.protocol.ProtocolCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests T252 to T259: no password hash leaves the server, in any of the shapes
 * a user is sent in.
 *
 * <h2>What these tests were written in response to</h2>
 * The protocol first registered the store's own adapter for users, which
 * writes every field a user has. Asking the codec what it actually emitted,
 * rather than reading the code and concluding it was fine, showed the SHA-256
 * password hash in all three forms a user travels in: on the login response,
 * as a single object, and inside the list of accounts an administrator loads.
 * One request would have handed a client the hash of every account in the
 * system.
 *
 * <p>Three shapes are tested separately because they take different paths
 * through Gson. An adapter registered for {@code User} alone is consulted only
 * when the declared type is {@code User}; a list of users and an object whose
 * runtime class is a subclass both fall through to plain reflection. That is
 * why the registration is a type hierarchy adapter, and why a test that
 * checked only the declared-type case would have passed while two ways of
 * leaking the hash remained open.
 *
 * <p>The field is found by reflection rather than named as a string, so the
 * test still looks for the right thing if the field is renamed, and still
 * fails if a second credential field is added and not handled.
 */
@DisplayName("Users on the wire carry no credential")
class ProtocolUserAdapterTest {

    private static final String PASSWORD = "secret123";

    private static User coordinator() {
        return new EmergencyCoordinator("U-01", "c.ordinator", PASSWORD,
                "C. Ordinator", "EC-001");
    }

    private static User responder() {
        return new DepartmentResponder("U-02", "fire.crew", PASSWORD,
                "F. Crew", "DEP-01");
    }

    private static User administrator() {
        return new SystemAdministrator("U-03", "admin", PASSWORD, "S. Admin");
    }

    /**
     * Every string field the user hierarchy holds whose name says it is a
     * credential, with the values they hold for this user.
     *
     * @param user the user
     * @return the values that must never appear on the wire
     */
    private static List<String> credentialValues(User user) {
        List<String> values = new ArrayList<>();
        for (Class<?> type = user.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                boolean looksSecret = name.contains("password") || name.endsWith("hash");
                if (!looksSecret || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(user);
                    if (value != null) {
                        values.add((String) value);
                    }
                } catch (IllegalAccessException e) {
                    throw new AssertionError("The field " + field.getName()
                            + " could not be read: " + e.getMessage(), e);
                }
            }
        }
        return values;
    }

    private static void assertCarriesNoCredential(String written, User user) {
        List<String> secrets = credentialValues(user);
        assertFalse(secrets.isEmpty(),
                "The test found no credential field on the user, so it would pass "
                        + "whatever the adapter did. Check the model.");
        for (String secret : secrets) {
            assertFalse(written.contains(secret),
                    "A credential was written onto the wire: " + written);
        }
        assertFalse(written.toLowerCase().contains("passwordhash"),
                "The password hash field was written onto the wire: " + written);
    }

    @Test
    @DisplayName("T252: a user serialised as User carries no hash")
    void declaredTypeCarriesNoHash() {
        User user = administrator();
        String written = ProtocolCodec.gson().toJson(user, User.class);

        assertCarriesNoCredential(written, user);
        assertTrue(written.contains("admin"), "The username should still be sent.");
    }

    @Test
    @DisplayName("T253: a user serialised by its runtime class carries no hash")
    void runtimeTypeCarriesNoHash() {
        // This is the call a handler makes when it passes an object without
        // naming its type, and it is the one that leaked.
        User user = coordinator();
        String written = ProtocolCodec.gson().toJson(user);

        assertCarriesNoCredential(written, user);
    }

    @Test
    @DisplayName("T254: a list of users carries no hash")
    void listCarriesNoHash() {
        List<User> users = List.of(coordinator(), responder(), administrator());
        String written = ProtocolCodec.gson().toJson(users);

        for (User user : users) {
            assertCarriesNoCredential(written, user);
        }
        assertTrue(written.contains("fire.crew"), "The accounts should still be sent.");
    }

    @Test
    @DisplayName("T255: the flag asking for a password change survives")
    void passwordChangeFlagSurvives() {
        // The field is called mustChangePassword, so a rule that dropped every
        // field whose name mentions a password would take it, and the client
        // would stop prompting for a new password. A boolean cannot be a
        // credential, which is why the rule exempts them.
        JsonObject written = ProtocolCodec.gson()
                .toJsonTree(administrator(), User.class).getAsJsonObject();

        assertTrue(written.has("mustChangePassword"),
                "The client needs this flag in order to prompt for a new password.");
        assertTrue(written.get("mustChangePassword").getAsBoolean());
    }

    @Test
    @DisplayName("T256: a responder arrives with the department its screens need")
    void responderKeepsItsDepartment() {
        String written = ProtocolCodec.gson().toJson(responder(), User.class);
        User read = ProtocolCodec.gson().fromJson(written, User.class);

        assertTrue(read instanceof DepartmentResponder,
                "A responder must come back as a responder, not as a plain user.");
        assertEquals("DEP-01", ((DepartmentResponder) read).getDepartmentId());
        assertEquals(UserRole.RESPONDER, read.getRole());
    }

    @Test
    @DisplayName("T257: each role comes back as its own class")
    void eachRoleComesBackAsItsOwnClass() {
        assertTrue(ProtocolCodec.gson().fromJson(
                ProtocolCodec.gson().toJson(coordinator(), User.class), User.class)
                instanceof EmergencyCoordinator);
        assertTrue(ProtocolCodec.gson().fromJson(
                ProtocolCodec.gson().toJson(administrator(), User.class), User.class)
                instanceof SystemAdministrator);
    }

    @Test
    @DisplayName("T258: the user a client reads back holds no hash to check against")
    void clientSideUserHoldsNoHash() {
        String written = ProtocolCodec.gson().toJson(administrator(), User.class);
        User read = ProtocolCodec.gson().fromJson(written, User.class);

        assertNotNull(read);
        assertNull(read.getPasswordHash(),
                "A client holding the hash could be modified to check passwords "
                        + "itself, which is the server's decision to make.");
        assertEquals("admin", read.getUsername());
        assertEquals("S. Admin", read.getFullName());
    }

    @Test
    @DisplayName("T259: a user with no role is refused rather than guessed at")
    void userWithoutRoleIsRefused() {
        JsonObject written = ProtocolCodec.gson()
                .toJsonTree(administrator(), User.class).getAsJsonObject();
        written.remove("role");

        assertThrows(JsonParseException.class,
                () -> ProtocolCodec.gson().fromJson(written, User.class));
    }
}
