package com.cqu.drs.model;

/**
 * How urgently a casualty needs treatment, using the four categories of the
 * standard field triage scheme.
 *
 * The colours are the ones used on physical triage tags, so what a responder
 * sees on the screen matches what is tied to the patient's wrist. The rank
 * orders the queue; it is separate from the enum's declaration order so that
 * reordering the constants cannot silently reorder treatment.
 *
 * <p>DECEASED is a triage category rather than a status, which is how field
 * triage works: it is a decision made at the scene about where effort goes,
 * recorded the same way as the others.
 */
public enum TriageCategory {

    IMMEDIATE("Immediate", "Red", 1),
    DELAYED("Delayed", "Yellow", 2),
    MINOR("Minor", "Green", 3),
    DECEASED("Deceased", "Black", 4);

    private final String label;
    private final String tagColour;
    private final int rank;

    TriageCategory(String label, String tagColour, int rank) {
        this.label = label;
        this.tagColour = tagColour;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    /**
     * The colour of the physical triage tag this category corresponds to.
     *
     * @return the colour
     */
    public String getTagColour() {
        return tagColour;
    }

    /**
     * Where this category sits in the treatment queue, lowest first.
     *
     * @return the rank
     */
    public int getRank() {
        return rank;
    }

    /**
     * Whether a casualty in this category needs transporting to hospital
     * before the others.
     *
     * @return true for the immediate category
     */
    public boolean isLifeThreatening() {
        return this == IMMEDIATE;
    }

    @Override
    public String toString() {
        return label + " (" + tagColour + ")";
    }
}
