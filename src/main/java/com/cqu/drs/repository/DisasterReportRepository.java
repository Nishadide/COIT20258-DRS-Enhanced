package com.cqu.drs.repository;

import com.cqu.drs.model.DisasterReport;

import java.util.List;
import java.util.Optional;

/**
 * Stores disaster reports. A report is kept with its assessment, its response
 * tasks and its alerts attached, because none of those has meaning apart from
 * the report they belong to.
 *
 * This is an interface so that the storage technology can be replaced without
 * touching the services above it. DRS-Initial stored reports as JSON files;
 * DRS-Enhanced stores them in MySQL, and the services are unaware of either.
 */
public interface DisasterReportRepository {

    /**
     * Every report held.
     *
     * @return the reports
     */
    List<DisasterReport> findAll();

    /**
     * Finds one report by its reference number.
     *
     * @param reference the reference to look for, of the form DR-nnnn
     * @return the report, or an empty Optional when none matches
     */
    Optional<DisasterReport> findByReference(String reference);

    /**
     * Saves a report, replacing any earlier version of the same one.
     *
     * @param report the report to save
     */
    void save(DisasterReport report);

    /**
     * How many reports are held, used to generate the next reference number.
     *
     * @return the count
     */
    int count();
}
