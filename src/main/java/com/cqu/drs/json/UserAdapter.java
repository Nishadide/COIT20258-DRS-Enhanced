package com.cqu.drs.json;

import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Handles the User hierarchy for Gson. User is abstract, so on reading a file
 * back Gson has no way of knowing which subclass to build. The role already
 * stored on every user is used as the discriminator, which avoids adding a
 * second field that could contradict it.
 */
public class UserAdapter implements JsonSerializer<User>, JsonDeserializer<User> {

    private static final String ROLE_FIELD = "role";

    @Override
    public JsonElement serialize(User source, Type type, JsonSerializationContext context) {
        if (source == null) {
            return null;
        }
        // Serialise against the concrete class so that subclass fields are written.
        return context.serialize(source, source.getClass());
    }

    @Override
    public User deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement roleElement = object.get(ROLE_FIELD);
        if (roleElement == null || roleElement.isJsonNull()) {
            throw new JsonParseException("A stored user has no role and cannot be read back.");
        }

        UserRole role = UserRole.valueOf(roleElement.getAsString());
        return switch (role) {
            case COORDINATOR -> context.deserialize(json, EmergencyCoordinator.class);
            case RESPONDER -> context.deserialize(json, DepartmentResponder.class);
            case ADMINISTRATOR -> context.deserialize(json, SystemAdministrator.class);
        };
    }
}
