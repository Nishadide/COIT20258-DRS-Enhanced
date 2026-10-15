package com.cqu.drs.json;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the adapter that handles the User hierarchy. User is abstract, so on
 * reading a file back Gson has no way of knowing which subclass to build. The
 * adapter uses the role already stored on every user to decide, which is what
 * these tests check.
 */
@DisplayName("UserAdapter")
class UserAdapterTest {

    private static final String PASSWORD = "Str0ngPass";
    private static final Type USER_LIST = new TypeToken<ArrayList<User>>() { }.getType();

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(User.class, new UserAdapter())
                .create();
    }

    @Test
    @DisplayName("A coordinator is written with its own fields, not only the shared ones")
    void serialisesSubclassFields() {
        User coordinator = new EmergencyCoordinator("U-01", "c.ordinator",
                PASSWORD, "C. Ordinator", "EC-001");

        String json = gson.toJson(coordinator, User.class);

        assertTrue(json.contains("\"coordinatorId\""),
                "The subclass field must be written, not dropped.");
        assertTrue(json.contains("COORDINATOR"));
    }

    @Test
    @DisplayName("A coordinator reads back as a coordinator")
    void restoresCoordinator() {
        User original = new EmergencyCoordinator("U-01", "c.ordinator",
                PASSWORD, "C. Ordinator", "EC-001");

        User restored = gson.fromJson(gson.toJson(original, User.class), User.class);

        assertInstanceOf(EmergencyCoordinator.class, restored);
        assertEquals("EC-001", ((EmergencyCoordinator) restored).getCoordinatorId());
        assertEquals(UserRole.COORDINATOR, restored.getRole());
    }

    @Test
    @DisplayName("A responder reads back as a responder, keeping its department")
    void restoresResponder() {
        User original = new DepartmentResponder("U-02", "fire.crew",
                PASSWORD, "Fire Crew", "DEP-01");

        User restored = gson.fromJson(gson.toJson(original, User.class), User.class);

        assertInstanceOf(DepartmentResponder.class, restored);
        assertEquals("DEP-01", ((DepartmentResponder) restored).getDepartmentId());
    }

    @Test
    @DisplayName("An administrator reads back as an administrator")
    void restoresAdministrator() {
        User original = new SystemAdministrator("U-03", "admin",
                PASSWORD, "System Administrator");

        User restored = gson.fromJson(gson.toJson(original, User.class), User.class);

        assertInstanceOf(SystemAdministrator.class, restored);
        assertEquals(UserRole.ADMINISTRATOR, restored.getRole());
    }

    @Test
    @DisplayName("The password digest survives, so a restored user can still sign in")
    void passwordDigestSurvives() {
        User original = new EmergencyCoordinator("U-04", "c.ordinator",
                PASSWORD, "C. Ordinator", "EC-002");

        User restored = gson.fromJson(gson.toJson(original, User.class), User.class);

        assertTrue(restored.authenticate(PASSWORD));
        assertFalse(restored.authenticate("WrongPassword"));
    }

    @Test
    @DisplayName("A mixed list of users reads back with each row the right class")
    void restoresMixedList() {
        List<User> users = Arrays.asList(
                new EmergencyCoordinator("U-05", "c.ordinator", PASSWORD, "C. O", "EC-003"),
                new DepartmentResponder("U-06", "fire.crew", PASSWORD, "F. Crew", "DEP-01"),
                new SystemAdministrator("U-07", "admin", PASSWORD, "S. Admin"));

        String json = gson.toJson(users, USER_LIST);
        List<User> restored = gson.fromJson(json, USER_LIST);

        assertEquals(3, restored.size());
        assertInstanceOf(EmergencyCoordinator.class, restored.get(0));
        assertInstanceOf(DepartmentResponder.class, restored.get(1));
        assertInstanceOf(SystemAdministrator.class, restored.get(2));
    }

    @Test
    @DisplayName("The active flag and the first-login flag survive")
    void flagsSurvive() {
        User original = new SystemAdministrator("U-08", "admin", PASSWORD, "S. Admin");
        original.setActive(false);
        original.changePassword("Replacement1");

        User restored = gson.fromJson(gson.toJson(original, User.class), User.class);

        assertFalse(restored.isActive());
        assertFalse(restored.isMustChangePassword());
    }

    @Test
    @DisplayName("A stored user with no role cannot be read back and says so")
    void refusesUserWithoutRole() {
        String json = "{\"userId\":\"U-09\",\"username\":\"broken\"}";
        assertThrows(JsonParseException.class, () -> gson.fromJson(json, User.class));
    }

    @Test
    @DisplayName("An absent user reads back as absent rather than failing")
    void handlesNull() {
        assertNull(gson.fromJson("null", User.class));
    }
}
