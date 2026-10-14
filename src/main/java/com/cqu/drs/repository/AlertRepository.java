package com.cqu.drs.repository;

import com.cqu.drs.model.Alert;

import java.util.List;

/**
 * Stores the warnings and evacuation instructions issued for a disaster,
 * together with whether each one was delivered.
 */
public interface AlertRepository {

    /**
     * Every alert held.
     *
     * @return the alerts
     */
    List<Alert> findAll();

    /**
     * Saves an alert.
     *
     * @param alert the alert to save
     */
    void save(Alert alert);
}
