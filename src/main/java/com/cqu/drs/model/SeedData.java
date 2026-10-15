package com.cqu.drs.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The reference data a usable system starts with: the eight responding
 * departments and the three staff accounts somebody can sign in as.
 *
 * <h2>Why this is in one place</h2>
 * It was in four. The JSON department store listed the eight departments, the
 * MySQL one listed them again, and the three starting accounts were written
 * out in both {@code ServerMain} and {@code AppContext}. Four lists of the
 * same facts, each correct on the day it was written.
 *
 * <p>Commit 15 made the duplication a problem rather than an untidiness,
 * because the submitted SQL script has to create the same rows. A fifth copy,
 * in a file nobody runs during development, would be the copy that silently
 * went stale: the application would seed one set of departments and the script
 * another, and the difference would be found by whoever ran the script and
 * then could not dispatch to a department the application expected.
 *
 * <p>So the list lives here, in the model, where both stores, both entry
 * points and the script generator can read it. It depends on nothing.
 *
 * <h2>The starting passwords are not secret, and are not meant to be</h2>
 * They are the documented credentials from Assessment 2, written so a marker
 * can sign in. Every account created from them is marked as needing a password
 * change, and the report records that a deployed system would seed no accounts
 * at all and would create the first administrator through a one-off setup
 * step instead.
 */
public final class SeedData {

    /** Not instantiable: this class holds reference data only. */
    private SeedData() {
    }

    /**
     * One account the system starts with.
     *
     * @param username      the login name
     * @param plainPassword the documented starting password
     * @param fullName      the person's name
     * @param role          the role held
     * @param departmentId  the department, required for a responder and null
     *                      otherwise
     */
    public record StartingAccount(String username, String plainPassword, String fullName,
                                  UserRole role, String departmentId) {

        /**
         * A stable identifier for this account, used when the account is
         * written by the SQL script rather than created by the application.
         *
         * Derived from the username rather than generated, because a script
         * run twice must write the same row twice rather than two rows, and a
         * random identifier would make the second run an insert.
         *
         * @return the identifier
         */
        public String seedUserId() {
            return "seed-" + username;
        }

        /**
         * Builds the user this account describes.
         *
         * @param userId the identifier to give it
         * @return the user, with the password already hashed by the model
         */
        public User toUser(String userId) {
            return switch (role) {
                case ADMINISTRATOR ->
                        new SystemAdministrator(userId, username, plainPassword, fullName);
                case COORDINATOR -> new EmergencyCoordinator(userId, username,
                        plainPassword, fullName,
                        DrsConstants.COORDINATOR_PREFIX + "001");
                case RESPONDER -> new DepartmentResponder(userId, username,
                        plainPassword, fullName, departmentId);
            };
        }
    }

    private static final String[][] DEPARTMENTS = {
        {"DEP-01", "Fire and Emergency", "dispatch@fire.gov.au"},
        {"DEP-02", "Hospital", "emergency@health.gov.au"},
        {"DEP-03", "Electricity", "faults@power.gov.au"},
        {"DEP-04", "Transportation", "control@transport.gov.au"},
        {"DEP-05", "Waste Management", "cleanup@waste.gov.au"},
        {"DEP-06", "Water Supply", "incidents@water.gov.au"},
        {"DEP-07", "Schools", "safety@education.gov.au"},
        {"DEP-08", "Law Enforcement", "operations@police.gov.au"}
    };

    private static final List<StartingAccount> ACCOUNTS = List.of(
            new StartingAccount("admin", "admin123", "System Administrator",
                    UserRole.ADMINISTRATOR, null),
            new StartingAccount("coordinator", "coord123", "Emergency Coordinator",
                    UserRole.COORDINATOR, null),
            new StartingAccount("fire.crew", "crew1234", "Fire and Emergency Crew",
                    UserRole.RESPONDER, "DEP-01"));

    /**
     * The eight departments named in the case study.
     *
     * A fresh list of fresh objects each time, because a caller is going to
     * change availability on one of them and a shared instance would change it
     * for everybody.
     *
     * @return the departments, all available
     */
    public static List<Department> departments() {
        List<Department> departments = new ArrayList<>(DEPARTMENTS.length);
        for (String[] row : DEPARTMENTS) {
            departments.add(new Department(row[0], row[1], row[2]));
        }
        return departments;
    }

    /**
     * The three accounts the system starts with, in the order they are
     * created.
     *
     * @return the accounts
     */
    public static List<StartingAccount> startingAccounts() {
        return Collections.unmodifiableList(ACCOUNTS);
    }
}
