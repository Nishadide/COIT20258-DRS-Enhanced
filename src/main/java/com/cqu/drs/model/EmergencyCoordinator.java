package com.cqu.drs.model;

/**
 * The central operator who assesses reported disasters, sets their priority,
 * allocates departments, issues public alerts and closes incidents.
 */
public class EmergencyCoordinator extends User {

    private String coordinatorId;

    /**
     * Creates a coordinator.
     *
     * @param userId        unique identifier
     * @param username      login name
     * @param plainPassword initial password
     * @param fullName      the person's name
     * @param coordinatorId the coordinator's operational identifier
     */
    public EmergencyCoordinator(String userId, String username, String plainPassword,
                                String fullName, String coordinatorId) {
        super(userId, username, plainPassword, fullName, UserRole.COORDINATOR);
        this.coordinatorId = coordinatorId;
    }

    /** No-argument constructor required by Gson. */
    protected EmergencyCoordinator() {
        super();
    }

    public String getCoordinatorId() {
        return coordinatorId;
    }

    public void setCoordinatorId(String coordinatorId) {
        this.coordinatorId = coordinatorId;
    }

    @Override
    public String toString() {
        return super.toString() + " [coordinator " + coordinatorId + "]";
    }
}
