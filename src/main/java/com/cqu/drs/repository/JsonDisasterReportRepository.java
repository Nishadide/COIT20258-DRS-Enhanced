package com.cqu.drs.repository;

import com.cqu.drs.model.DisasterReport;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JSON-file implementation. Stores disaster reports. A report is saved with its assessment, its response
 * tasks and its alerts nested inside it, because none of those has meaning
 * apart from the report they belong to.
 */
public class JsonDisasterReportRepository implements DisasterReportRepository {

    private static final String FILE_NAME = "reports.json";
    private static final Type LIST_TYPE = new TypeToken<ArrayList<DisasterReport>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates a repository backed by the store given.
     *
     * @param store the data store to read and write through
     */
    public JsonDisasterReportRepository(JsonDataStore store) {
        this.store = store;
    }

    /**
     * Every report held, in the order they were stored.
     *
     * @return the reports, or an empty list when none are held
     */
    @Override
    public List<DisasterReport> findAll() {
        return store.load(FILE_NAME, LIST_TYPE);
    }

    /**
     * Finds one report by its reference number.
     *
     * @param reference the reference to look for
     * @return the report, or an empty Optional when no report has that reference
     */
    @Override
    public Optional<DisasterReport> findByReference(String reference) {
        if (reference == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(r -> reference.equals(r.getReferenceNumber()))
                .findFirst();
    }

    /**
     * Saves a report, replacing any earlier version of the same report.
     *
     * @param report the report to save
     */
    @Override
    public void save(DisasterReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Cannot save a null report.");
        }
        List<DisasterReport> all = findAll();
        all.removeIf(r -> report.getReferenceNumber().equals(r.getReferenceNumber()));
        all.add(report);
        store.store(FILE_NAME, all);
    }

    /**
     * {@inheritDoc}
     *
     * One process writes this file, so checking and then storing cannot be
     * interrupted by another writer. The check is still made, because a
     * reference used twice is a fault whatever the storage is, and the
     * services above must behave the same whichever repository is in use.
     */
    @Override
    public void insert(DisasterReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Cannot save a null report.");
        }
        if (findByReference(report.getReferenceNumber()).isPresent()) {
            throw new DuplicateReferenceException(report.getReferenceNumber(), null);
        }
        save(report);
    }

    /**
     * The number of reports held, used when generating the next reference.
     *
     * @return the count of stored reports
     */
    @Override
    public int count() {
        return findAll().size();
    }
}
