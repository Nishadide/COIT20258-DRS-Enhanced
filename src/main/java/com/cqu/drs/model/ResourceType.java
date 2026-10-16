package com.cqu.drs.model;

/**
 * The kinds of resource a department holds and a coordinator can call on.
 *
 * Each carries the noun its units are counted in, because "4 units" on a
 * screen is less use to a coordinator than "4 crews" or "4 beds", and the
 * difference matters when the request is urgent.
 */
public enum ResourceType {

    PERSONNEL("Personnel", "crew"),
    VEHICLE("Vehicle", "vehicle"),
    EQUIPMENT("Equipment", "unit"),
    MEDICAL("Medical", "bed"),
    SHELTER("Shelter", "place");

    private final String label;
    private final String unitNoun;

    ResourceType(String label, String unitNoun) {
        this.label = label;
        this.unitNoun = unitNoun;
    }

    /**
     * The name shown on a screen.
     *
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * What one unit of this resource is called.
     *
     * @return the singular noun
     */
    public String getUnitNoun() {
        return unitNoun;
    }

    /**
     * Describes a quantity of this resource in words.
     *
     * @param units how many
     * @return text such as "3 crews" or "1 bed"
     */
    public String describe(int units) {
        return units + " " + unitNoun + (units == 1 ? "" : "s");
    }
}
