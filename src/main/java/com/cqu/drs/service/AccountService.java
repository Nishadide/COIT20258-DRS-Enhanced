package com.cqu.drs.service;

import com.cqu.drs.model.DrsConstants;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;
import com.cqu.drs.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and maintains staff accounts. Only an administrator reaches this
 * service, and every change it makes is written to the audit log.
 */
public class AccountService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Creates the service.
     *
     * @param userRepository where accounts are held
     * @param auditService   used to record account changes
     */
    public AccountService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Creates an account. The login name must not already be in use, and the
     * user is required to replace the initial password when they first sign in.
     *
     * @param username      the login name
     * @param plainPassword the initial password
     * @param fullName      the person's name
     * @param role          the role to grant
     * @param departmentId  the department, required for a responder and ignored otherwise
     * @param createdBy     the username of the administrator
     * @return the account created
     * @throws IllegalArgumentException when the login name is taken or details are invalid
     */
    public User create(String username, String plainPassword, String fullName,
                       UserRole role, String departmentId, String createdBy) {
        if (role == null) {
            throw new IllegalArgumentException("A role must be given.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException(
                    "The username " + username + " is already in use.");
        }

        String userId = UUID.randomUUID().toString();
        User user = switch (role) {
            case COORDINATOR -> new EmergencyCoordinator(userId, username, plainPassword,
                    fullName, DrsConstants.COORDINATOR_PREFIX
                            + userId.substring(0, DrsConstants.COORDINATOR_CODE_LENGTH));
            case RESPONDER -> {
                if (departmentId == null || departmentId.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "A responder must belong to a department.");
                }
                yield new DepartmentResponder(userId, username, plainPassword,
                        fullName, departmentId);
            }
            case ADMINISTRATOR -> new SystemAdministrator(userId, username,
                    plainPassword, fullName);
        };

        userRepository.save(user);
        auditService.record(createdBy, "Created account " + username
                + " with role " + role.name());
        return user;
    }

    /**
     * Deactivates an account, which prevents it being used to sign in without
     * removing the record of what that user did.
     *
     * @param username      the account to deactivate
     * @param deactivatedBy the username of the administrator
     * @throws IllegalArgumentException when the account cannot be found
     */
    public void deactivate(String username, String deactivatedBy) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account is named " + username + "."));
        user.setActive(false);
        userRepository.save(user);
        auditService.record(deactivatedBy, "Deactivated account " + username);
    }

    /**
     * Returns a deactivated account to use. An account that is already active
     * is left alone, so pressing the control twice does no harm.
     *
     * @param username    the account to activate
     * @param activatedBy the username of the administrator
     * @return true when the account was inactive and has now been activated
     * @throws IllegalArgumentException when the account cannot be found
     */
    public boolean activate(String username, String activatedBy) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account is named " + username + "."));
        if (user.isActive()) {
            return false;
        }
        user.setActive(true);
        userRepository.save(user);
        auditService.record(activatedBy, "Reactivated account " + username);
        return true;
    }

    /**
     * Changes the role held by an account.
     *
     * @param username   the account to change
     * @param role       the role to grant
     * @param changedBy  the username of the administrator
     * @throws IllegalArgumentException when the account cannot be found
     */
    public void assignRole(String username, UserRole role, String changedBy) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account is named " + username + "."));
        user.setRole(role);
        userRepository.save(user);
        auditService.record(changedBy, "Changed role of " + username
                + " to " + role.name());
    }

    /**
     * Every account held.
     *
     * @return the accounts
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Finds one account by login name.
     *
     * @param username the name to look for
     * @return the account, or an empty Optional
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
