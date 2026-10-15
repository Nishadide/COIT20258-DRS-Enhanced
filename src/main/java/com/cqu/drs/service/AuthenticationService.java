package com.cqu.drs.service;

import com.cqu.drs.model.User;
import com.cqu.drs.repository.UserRepository;

import java.util.Optional;

/**
 * Signs staff in and out. Reporting is open to the public, so this service is
 * only involved when a member of staff needs access to a role-restricted
 * function.
 */
public class AuthenticationService implements AuthenticationOperations {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private User currentUser;

    /**
     * Creates the service.
     *
     * @param userRepository where accounts are held
     * @param auditService   used to record sign-ins and sign-outs
     */
    public AuthenticationService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Signs a user in. A sign-in is refused when the account is unknown, the
     * password is wrong, or the account has been deactivated. The three cases
     * are not distinguished to the caller, so that an attacker cannot learn
     * which usernames exist.
     *
     * @param username the login name offered
     * @param password the password offered
     * @return the user when the sign-in succeeds, otherwise an empty Optional
     */
    @Override
    public Optional<User> login(String username, String password) {
        Optional<User> authenticated = authenticate(username, password);
        authenticated.ifPresent(user -> this.currentUser = user);
        return authenticated;
    }

    /**
     * Checks credentials without remembering anyone.
     *
     * This exists because the field this class keeps the signed-in user in is
     * a single-user idea. One person at one keyboard has one current user, and
     * for DRS-Initial that was exactly right. A server has as many current
     * users as it has connected clients, and they would all overwrite the same
     * field: the last person to sign in would become "the current user" for
     * everyone, which is not a cosmetic fault but an access control one.
     *
     * <p>So the server does not use {@link #login}. It calls this, and keeps
     * the result against the session token in its own registry, where one
     * client's session cannot reach another's.
     *
     * @param username the login name offered
     * @param password the password offered
     * @return the user when the credentials are good, otherwise an empty
     *         Optional
     */
    public Optional<User> authenticate(String username, String password) {
        Optional<User> found = userRepository.findByUsername(username);
        if (found.isEmpty()) {
            auditService.record(username, "Failed sign-in: unknown username");
            return Optional.empty();
        }
        User user = found.get();
        if (!user.isActive()) {
            auditService.record(username, "Failed sign-in: account deactivated");
            return Optional.empty();
        }
        if (!user.authenticate(password)) {
            auditService.record(username, "Failed sign-in: incorrect password");
            return Optional.empty();
        }
        auditService.record(username, "Signed in");
        return Optional.of(user);
    }

    /**
     * Signs the current user out and ends the session.
     */
    @Override
    public void logout() {
        if (currentUser != null) {
            auditService.record(currentUser.getUsername(), "Signed out");
            currentUser = null;
        }
    }

    /**
     * The user currently signed in.
     *
     * @return the user, or an empty Optional when nobody is signed in
     */
    @Override
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /**
     * Replaces the password of the user signed in and saves the change.
     *
     * @param newPassword the replacement password
     * @throws IllegalStateException if nobody is signed in
     */
    @Override
    public void changeCurrentPassword(String newPassword) {
        if (currentUser == null) {
            throw new IllegalStateException("Nobody is signed in.");
        }
        changePassword(currentUser.getUsername(), newPassword);
    }

    /**
     * Replaces one named account's password.
     *
     * Named rather than implied, for the same reason {@link #authenticate}
     * exists: on a server there is no single current user, and the account to
     * change is the one belonging to the session the request arrived on.
     *
     * @param username    the account to change
     * @param newPassword the replacement password
     * @throws IllegalArgumentException when no such account exists
     */
    public void changePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account is named " + username + "."));
        user.changePassword(newPassword);
        userRepository.save(user);
        auditService.record(username, "Changed password");

        if (currentUser != null && currentUser.getUsername().equals(username)) {
            // Keep the remembered copy in step, so a later check against it
            // uses the new password rather than the old one.
            this.currentUser = user;
        }
    }

    /**
     * Records a sign-out for a named account.
     *
     * The server has no "current user" to sign out, only the session the
     * request came in on, so it says whose sign-out it is.
     *
     * @param username the account signing out
     */
    public void recordLogout(String username) {
        auditService.record(username, "Signed out");
    }
}
