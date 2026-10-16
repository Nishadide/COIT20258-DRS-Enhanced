package com.cqu.drs.service;

import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.model.TriageCategory;

import java.util.List;
import java.util.Map;

/**
 * Recording the people hurt in a disaster, triaging them, and referring them
 * to hospital.
 *
 * Implemented twice, like every other set of operations: once by the service
 * that reaches the repository, and once by the client, which asks the server.
 */
public interface CasualtyOperations {

    /**
     * Records a casualty at the scene.
     *
     * @param reference     the disaster they were found in
     * @param name          their name, or null when not yet identified
     * @param age           their age, or null when unknown
     * @param contactNumber a contact number, or null
     * @param triage        how urgently they need treatment
     * @param location      where at the scene they were found
     * @param notes         anything that needs recording
     * @param registeredBy  who is recording them
     * @return the record created
     */
    Casualty register(String reference, String name, Integer age, String contactNumber,
                      TriageCategory triage, String location, String notes,
                      String registeredBy);

    /**
     * Changes a casualty's triage category.
     *
     * @param casualtyId the casualty
     * @param triage     the new category
     * @param changedBy  who made the judgement
     * @return the record as it now stands
     */
    Casualty retriage(String casualtyId, TriageCategory triage, String changedBy);

    /**
     * Records that a casualty has been sent to a hospital.
     *
     * @param casualtyId the casualty
     * @param hospital   where they were sent
     * @param referredBy who sent them
     * @return the record as it now stands
     */
    Casualty refer(String casualtyId, String hospital, String referredBy);

    /**
     * Records a change of status that is not a referral.
     *
     * @param casualtyId the casualty
     * @param status     the new status
     * @param changedBy  who changed it
     * @return the record as it now stands
     */
    Casualty updateStatus(String casualtyId, CasualtyStatus status, String changedBy);

    /**
     * Everyone recorded against one disaster, most urgent first.
     *
     * @param reference the disaster
     * @return the casualties
     */
    List<Casualty> findForReport(String reference);

    /**
     * How many casualties fall into each triage category.
     *
     * Counts only. This is the one casualty operation an administrator may
     * ask, because a count tells them how large the response is without
     * telling them anything about any individual.
     *
     * @param reference the disaster
     * @return the count for each category, including the empty ones
     */
    Map<TriageCategory, Integer> summaryFor(String reference);
}
