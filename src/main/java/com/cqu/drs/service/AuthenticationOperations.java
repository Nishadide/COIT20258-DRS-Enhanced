package com.cqu.drs.service;

import com.cqu.drs.model.User;

import java.util.Optional;

/**
 * Signing in and out.
 *
 * Implemented locally by {@link AuthenticationService}, which checks the
 * password itself, and remotely by the client, which asks the server to. The
 * screens call the same methods either way.
 *
 * <p>The password is only ever sent, never checked, on the client. A client
 * that decided for itself whether a password was right would be trusting the
 * machine the password was typed on, which is the one machine an attacker
 * controls.
 */
public interface AuthenticationOperations {

    /**
     * Signs a user in.
     *
     * A refusal does not say which of the three reasons applied, so that an
     * attacker cannot learn which usernames exist.
     *
     * @param username the login name offered
     * @param password the password offered
     * @return the user when the sign-in succeeds, otherwise an empty Optional
     */
    Optional<User> login(String username, String password);

    /** Signs the current user out and ends the session. */
    void logout();

    /**
     * The user currently signed in.
     *
     * @return the user, or an empty Optional when nobody is signed in
     */
    Optional<User> getCurrentUser();

    /**
     * Changes the signed-in user's password.
     *
     * @param newPassword the new password
     * @throws IllegalStateException when nobody is signed in
     */
    void changeCurrentPassword(String newPassword);
}
