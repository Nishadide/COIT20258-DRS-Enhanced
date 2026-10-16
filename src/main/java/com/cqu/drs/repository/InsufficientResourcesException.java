package com.cqu.drs.repository;

/**
 * A request for more units than the pool had when the request reached the
 * front of the queue.
 *
 * <h2>Why this is its own exception</h2>
 * It is the ordinary outcome this feature exists to produce. Two coordinators
 * ask for the last crew; one gets it and one is told there are none left. The
 * second is not an error in the request, not a fault in the system, and not
 * something trying again will fix, so it needs to be distinguishable from
 * every other failure a repository can raise.
 *
 * <p>It carries the number actually available, because a coordinator told
 * "not enough" will immediately ask how many there are, and the answer is
 * already known at the moment of refusal. Reading it again afterwards would
 * give a different number anyway, since by then somebody else may have taken
 * more.
 */
public class InsufficientResourcesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String resourceName;
    private final int unitsRequested;
    private final int unitsAvailable;

    /**
     * Creates the refusal.
     *
     * @param resourceName   what was asked for
     * @param unitsRequested how many were wanted
     * @param unitsAvailable how many there were at that moment
     */
    public InsufficientResourcesException(String resourceName, int unitsRequested,
                                          int unitsAvailable) {
        super(resourceName + " has " + unitsAvailable + " of "
                + unitsRequested + " requested units available.");
        this.resourceName = resourceName;
        this.unitsRequested = unitsRequested;
        this.unitsAvailable = unitsAvailable;
    }

    public String getResourceName() {
        return resourceName;
    }

    public int getUnitsRequested() {
        return unitsRequested;
    }

    public int getUnitsAvailable() {
        return unitsAvailable;
    }
}
