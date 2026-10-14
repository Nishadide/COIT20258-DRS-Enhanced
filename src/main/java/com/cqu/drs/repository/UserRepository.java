package com.cqu.drs.repository;

import com.cqu.drs.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Stores staff accounts. The concrete implementation decides how a User and
 * its three subclasses are written and read back.
 */
public interface UserRepository {

    /**
     * Every account held.
     *
     * @return the accounts
     */
    List<User> findAll();

    /**
     * Finds one account by login name, ignoring case.
     *
     * @param username the name to look for
     * @return the account, or an empty Optional when none matches
     */
    Optional<User> findByUsername(String username);

    /**
     * Reports whether a login name is already taken.
     *
     * @param username the name to check
     * @return true when an account already holds it
     */
    boolean existsByUsername(String username);

    /**
     * Saves an account, replacing any earlier version of the same one.
     *
     * @param user the account to save
     */
    void save(User user);
}
