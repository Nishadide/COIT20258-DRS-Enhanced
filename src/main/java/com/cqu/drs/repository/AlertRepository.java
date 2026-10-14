package com.cqu.drs.repository;

import com.cqu.drs.model.Alert;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores issued alerts. Alerts are also held on the report they belong to;
 * this file keeps them together so that every alert issued can be reviewed
 * without walking through every report.
 */
public class AlertRepository {

    private static final String FILE_NAME = "alerts.json";
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Alert>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates a repository backed by the store given.
     *
     * @param store the data store to read and write through
     */
    public AlertRepository(JsonDataStore store) {
        this.store = store;
    }

    /**
     * Every alert held, oldest first.
     *
     * @return the alerts, or an empty list when none are held
     */
    public List<Alert> findAll() {
        return store.load(FILE_NAME, LIST_TYPE);
    }

    /**
     * Saves an alert, replacing any earlier version of the same alert.
     *
     * @param alert the alert to save
     */
    public void save(Alert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("Cannot save a null alert.");
        }
        List<Alert> all = findAll();
        all.removeIf(a -> alert.getAlertId().equals(a.getAlertId()));
        all.add(alert);
        store.store(FILE_NAME, all);
    }
}
