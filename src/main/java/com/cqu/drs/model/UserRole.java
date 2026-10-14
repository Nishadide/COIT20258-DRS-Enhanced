package com.cqu.drs.model;

/**
 * The roles an authenticated user may hold. A member of the public who reports
 * a disaster holds no role, because reporting requires no account.
 */
public enum UserRole {

    COORDINATOR("Emergency Coordinator"),
    RESPONDER("Department Responder"),
    ADMINISTRATOR("System Administrator");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
