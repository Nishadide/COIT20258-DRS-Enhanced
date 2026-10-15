package com.cqu.drs.service;

import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * Managing staff accounts. Every operation here is for administrators only,
 * which the server enforces from the role on the session.
 */
public interface AccountOperations {

    /**
     * Creates an account.
     *
     * @param username      the login name
     * @param plainPassword the initial password
     * @param fullName      the person's name
     * @param role          the role held
     * @param departmentId  the department, required for a responder
     * @param createdBy     the administrator's username
     * @return the account created
     */
    User create(String username, String plainPassword, String fullName,
                UserRole role, String departmentId, String createdBy);

    /**
     * Stops an account being used, without removing what it did.
     *
     * @param username      the account
     * @param deactivatedBy the administrator's username
     */
    void deactivate(String username, String deactivatedBy);

    /**
     * Lets a deactivated account be used again.
     *
     * @param username    the account
     * @param activatedBy the administrator's username
     * @return true when the account changed, false when it was already active
     */
    boolean activate(String username, String activatedBy);

    /**
     * Changes the role an account holds.
     *
     * @param username  the account
     * @param role      the new role
     * @param changedBy the administrator's username
     */
    void assignRole(String username, UserRole role, String changedBy);

    /**
     * Every account.
     *
     * @return the accounts
     */
    List<User> findAll();

    /**
     * Finds one account.
     *
     * @param username the login name
     * @return the account, or an empty Optional
     */
    Optional<User> findByUsername(String username);
}
