package com.cqu.drs.repository;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Teaches Gson how to read and write LocalDateTime, which it cannot handle on
 * its own. Values are stored in ISO form, so the files remain readable and
 * sort correctly as text.
 */
public class LocalDateTimeAdapter
        implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public JsonElement serialize(LocalDateTime source, Type type,
                                 JsonSerializationContext context) {
        return source == null ? null : new JsonPrimitive(FORMATTER.format(source));
    }

    @Override
    public LocalDateTime deserialize(JsonElement json, Type type,
                                     JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        return LocalDateTime.parse(json.getAsString(), FORMATTER);
    }
}
