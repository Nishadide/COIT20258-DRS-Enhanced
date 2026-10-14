package com.cqu.drs.service;

import com.cqu.drs.model.User;
import com.cqu.drs.repository.UserRepository;

import java.util.Optional;

/**
 * Signs staff in and out. Reporting is open to the public, so this service is
 * only involved when a member of staff needs access to a role-restricted
 * function.
 */
public class AuthenticationService {

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
    public Optional<User> login(String username, String password) {
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
        this.currentUser = user;
        auditService.record(username, "Signed in");
        return Optional.of(user);
    }

    /**
     * Signs the current user out and ends the session.
     */
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
    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    /**
     * Replaces the password of the user signed in and saves the change.
     *
     * @param newPassword the replacement password
     * @throws IllegalStateException if nobody is signed in
     */
    public void changeCurrentPassword(String newPassword) {
        if (currentUser == null) {
            throw new IllegalStateException("Nobody is signed in.");
        }
        currentUser.changePassword(newPassword);
        userRepository.save(currentUser);
        auditService.record(currentUser.getUsername(), "Changed password");
    }
}
