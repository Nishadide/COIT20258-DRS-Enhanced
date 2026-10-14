package com.cqu.drs.model;

/**
 * The administrator who manages accounts and roles and reviews the audit log.
 */
public class SystemAdministrator extends User {

    /**
     * Creates an administrator.
     *
     * @param userId        unique identifier
     * @param username      login name
     * @param plainPassword initial password
     * @param fullName      the person's name
     */
    public SystemAdministrator(String userId, String username, String plainPassword,
                               String fullName) {
        super(userId, username, plainPassword, fullName, UserRole.ADMINISTRATOR);
    }

    /** No-argument constructor required by Gson. */
    protected SystemAdministrator() {
        super();
    }

    @Override
    public String toString() {
        return super.toString() + " [administrator]";
    }
}
