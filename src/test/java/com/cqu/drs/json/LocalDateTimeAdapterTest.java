package com.cqu.drs.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the adapter that teaches Gson to read and write LocalDateTime. Gson
 * cannot handle java.time on its own, so without this adapter every timestamp
 * in the system would be stored wrongly or not at all.
 */
@DisplayName("LocalDateTimeAdapter")
class LocalDateTimeAdapterTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
    }

    @Test
    @DisplayName("A timestamp is written in ISO form")
    void serialisesInIsoForm() {
        LocalDateTime moment = LocalDateTime.of(2026, 9, 15, 9, 17, 9);
        String json = gson.toJson(moment, LocalDateTime.class);
        assertEquals("\"2026-09-15T09:17:09\"", json);
    }

    @Test
    @DisplayName("A timestamp survives being written and read back")
    void survivesRoundTrip() {
        LocalDateTime moment = LocalDateTime.of(2026, 9, 15, 9, 17, 9);
        String json = gson.toJson(moment, LocalDateTime.class);
        LocalDateTime restored = gson.fromJson(json, LocalDateTime.class);
        assertEquals(moment, restored);
    }

    @Test
    @DisplayName("Fractions of a second are not lost")
    void keepsSubSecondPrecision() {
        LocalDateTime moment = LocalDateTime.of(2026, 9, 15, 9, 17, 9, 983920700);
        LocalDateTime restored = gson.fromJson(
                gson.toJson(moment, LocalDateTime.class), LocalDateTime.class);
        assertEquals(moment, restored);
    }

    @Test
    @DisplayName("Midnight at the turn of the year survives the round trip")
    void handlesBoundaryMoment() {
        LocalDateTime moment = LocalDateTime.of(2027, 1, 1, 0, 0, 0);
        LocalDateTime restored = gson.fromJson(
                gson.toJson(moment, LocalDateTime.class), LocalDateTime.class);
        assertEquals(moment, restored);
    }

    @Test
    @DisplayName("A missing timestamp reads back as absent rather than failing")
    void handlesNull() {
        LocalDateTime restored = gson.fromJson("null", LocalDateTime.class);
        assertNull(restored);
    }

    @Test
    @DisplayName("The stored form is readable text, not a set of internal fields")
    void storedFormIsReadable() {
        String json = gson.toJson(LocalDateTime.of(2026, 9, 15, 9, 17, 9),
                LocalDateTime.class);
        assertTrue(json.startsWith("\""),
                "The value should be stored as a string, not as an object.");
        assertFalse(json.contains("dayOfMonth"),
                "Gson must not fall back to writing the internal fields.");
        assertFalse(json.contains("nano"),
                "Gson must not fall back to writing the internal fields.");
    }
}
