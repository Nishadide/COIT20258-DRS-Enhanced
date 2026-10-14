package com.cqu.drs.repository;

import com.cqu.drs.model.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the JSON files that hold the system's data. Every
 * repository goes through this class, so the storage format is decided in one
 * place: swapping JSON for another format would change this class alone and
 * leave the repositories and the services untouched.
 */
public class JsonDataStore {

    /** Folder used when none is given. Relative, so the project is portable. */
    public static final String DEFAULT_DATA_DIR = "data";

    private final Path dataDir;
    private final Gson gson;

    /**
     * Creates a store writing to the default data folder.
     */
    public JsonDataStore() {
        this(Paths.get(DEFAULT_DATA_DIR));
    }

    /**
     * Creates a store writing to the folder given. Tests use this constructor
     * with a temporary folder so that they never touch the real data.
     *
     * @param dataDir the folder the files live in
     */
    public JsonDataStore(Path dataDir) {
        this.dataDir = dataDir;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(User.class, new UserAdapter())
                .create();
        ensureDataDir();
    }

    /**
     * Creates the data folder if it is not already there.
     */
    private void ensureDataDir() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the data folder " + dataDir, e);
        }
    }

    /**
     * Reads a list from a file. A file that is missing or empty yields an
     * empty list rather than an error, so the system starts cleanly the first
     * time it is run.
     *
     * @param <T>      the element type
     * @param fileName the file to read, for example reports.json
     * @param listType the type of the list, from a TypeToken
     * @return the items read, or an empty list
     */
    public <T> List<T> load(String fileName, Type listType) {
        Path file = dataDir.resolve(fileName);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<T> items = gson.fromJson(reader, listType);
            return items == null ? new ArrayList<>() : items;
        } catch (IOException | JsonSyntaxException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    /**
     * Writes a list to a file, replacing whatever was there.
     *
     * @param <T>      the element type
     * @param fileName the file to write
     * @param items    the items to store
     */
    public <T> void store(String fileName, List<T> items) {
        Path file = dataDir.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(items, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write " + file, e);
        }
    }

    /**
     * The folder this store writes to.
     *
     * @return the data folder
     */
    public Path getDataDir() {
        return dataDir;
    }
}
