package com.cqu.drs.repository;

import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.TriageCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where casualty records are stored.
 *
 * The implementations encrypt the personal fields on the way in and decrypt
 * them on the way out, so nothing above the data tier handles ciphertext and
 * nothing above the data tier can forget to encrypt. That is the reason the
 * encryption lives here rather than in the service: a service can be bypassed
 * by a second service, and a repository is the only door to the table.
 */
public interface CasualtyRepository {

    /**
     * Stores a casualty, inserting it or replacing what is held.
     *
     * @param casualty the record
     */
    void save(Casualty casualty);

    /**
     * Finds one casualty.
     *
     * @param casualtyId the identifier
     * @return the casualty, or an empty Optional
     */
    Optional<Casualty> findById(String casualtyId);

    /**
     * Everyone recorded against one disaster, most urgent first.
     *
     * @param reference the disaster
     * @return the casualties
     */
    List<Casualty> findForReport(String reference);

    /**
     * How many casualties fall into each triage category for one disaster.
     *
     * Counted by the database rather than by reading the records and counting
     * them in Java, because the counts are shown to people who may not read
     * the records themselves: an administrator may see how many are in each
     * category and may not see who they are. Computing it from the records
     * would mean decrypting names in order to produce a number that does not
     * contain them.
     *
     * @param reference the disaster
     * @return the count for each category, including the categories with none
     */
    Map<TriageCategory, Integer> countByTriage(String reference);

    /**
     * How many casualties are recorded in total, used by the tests and by the
     * summary.
     *
     * @return the count
     */
    int count();
}
