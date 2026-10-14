package com.cqu.drs.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * A member of staff who signs in to the system. The three concrete roles
 * extend this class, so the authentication behaviour is written once.
 *
 * Passwords are never held in plain text. They are stored as a SHA-256 digest,
 * which is enough for a prototype; a production system would use a salted,
 * deliberately slow algorithm such as bcrypt.
 */
public abstract class User {

    private String userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private UserRole role;
    private boolean active;
    private boolean mustChangePassword;

    /**
     * Creates a user with an initial password.
     *
     * @param userId          unique identifier
     * @param username        login name
     * @param plainPassword   initial password, stored only as a digest
     * @param fullName        the person's name
     * @param role            the role this user holds
     */
    protected User(String userId, String username, String plainPassword,
                   String fullName, UserRole role) {
        this.userId = userId;
        setUsername(username);
        setFullName(fullName);
        this.passwordHash = hash(requireValidPassword(plainPassword));
        this.role = role;
        this.active = true;
        this.mustChangePassword = true;
    }

    /** No-argument constructor required by Gson. */
    protected User() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public final void setUsername(String username) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.length() < DrsConstants.USERNAME_MIN_LENGTH
                || trimmed.length() > DrsConstants.USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Username must be between "
                    + DrsConstants.USERNAME_MIN_LENGTH + " and "
                    + DrsConstants.USERNAME_MAX_LENGTH + " characters.");
        }
        this.username = trimmed;
    }

    public String getFullName() {
        return fullName;
    }

    public final void setFullName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        if (trimmed.length() < DrsConstants.FULL_NAME_MIN_LENGTH
                || trimmed.length() > DrsConstants.FULL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Full name must be between "
                    + DrsConstants.FULL_NAME_MIN_LENGTH + " and "
                    + DrsConstants.FULL_NAME_MAX_LENGTH + " characters.");
        }
        this.fullName = trimmed;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    /** Exposed for the repository only; the digest is never shown to a user. */
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Checks a password against the stored digest. A null or empty password is
     * rejected rather than causing an error.
     *
     * @param plainPassword the password offered
     * @return true when the password matches
     */
    public boolean authenticate(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            return false;
        }
        return hash(plainPassword).equals(passwordHash);
    }

    /**
     * Replaces the password. The new password must meet the minimum length,
     * and the old one stops working as soon as it is replaced.
     *
     * @param newPassword the replacement password
     * @throws IllegalArgumentException if the new password is too short
     */
    public void changePassword(String newPassword) {
        this.passwordHash = hash(requireValidPassword(newPassword));
        this.mustChangePassword = false;
    }

    /**
     * Checks a candidate password and returns it when acceptable.
     *
     * @param plainPassword the password to check
     * @return the same password
     * @throws IllegalArgumentException if it is null or too short
     */
    private static String requireValidPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.length() < DrsConstants.PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least "
                    + DrsConstants.PASSWORD_MIN_LENGTH + " characters.");
        }
        return plainPassword;
    }

    /**
     * Produces a SHA-256 digest of the text given.
     *
     * @param text the text to digest
     * @return the digest, Base64 encoded
     */
    private static String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ", " + role.getLabel() + ")"
                + (active ? "" : " [deactivated]");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        return Objects.equals(userId, ((User) o).userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
