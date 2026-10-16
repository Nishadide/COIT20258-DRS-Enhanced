package com.cqu.drs.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A person injured, missing or killed in a disaster, recorded at the scene.
 *
 * <h2>This is the most sensitive record in the system</h2>
 * A name, a contact number and a note about someone's injuries, tied to a
 * place and a time. It is the record that would do real harm if it leaked, and
 * it is why the three fields holding personal detail are encrypted in the
 * database rather than merely access controlled. An access control stops
 * somebody asking the application; encryption also stops somebody reading the
 * table.
 *
 * <p>The encryption happens in the repository, on the way in and out, so this
 * class holds plain values and nothing above the data tier has to remember to
 * encrypt. The fields that are encrypted are named in PROTOCOL.md 7.3 rather
 * than being decided quietly here.
 *
 * <h2>A casualty may be unidentified, and that is a normal record</h2>
 * The name is optional. At a scene people are recorded before they can be
 * identified, and a registry that refused to record a casualty without a name
 * would simply not record them, which is worse than recording them as
 * unidentified. The same applies to the contact number.
 */
public class Casualty {

    private String casualtyId;
    private String referenceNumber;
    private String name;
    private Integer age;
    private String contactNumber;
    private String notes;
    private TriageCategory triage;
    private CasualtyStatus status;
    private String location;
    private String referredTo;
    private LocalDateTime referredAt;
    private String registeredBy;
    private LocalDateTime registeredAt;
    private String lastUpdatedBy;
    private LocalDateTime lastUpdatedAt;

    /**
     * Records a casualty at the scene.
     *
     * @param casualtyId      unique identifier
     * @param referenceNumber the disaster they were found in
     * @param name            their name, or null when not yet identified
     * @param age             their age, or null when unknown
     * @param contactNumber   a contact number, or null
     * @param triage          how urgently they need treatment
     * @param location        where at the scene they were found
     * @param notes           anything the responder needs recorded
     * @param registeredBy    who recorded them
     */
    public Casualty(String casualtyId, String referenceNumber, String name, Integer age,
                    String contactNumber, TriageCategory triage, String location,
                    String notes, String registeredBy) {
        if (referenceNumber == null || referenceNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "A casualty must be recorded against a disaster.");
        }
        if (triage == null) {
            throw new IllegalArgumentException("A casualty must be triaged.");
        }
        if (age != null && (age < 0 || age > 130)) {
            throw new IllegalArgumentException("An age of " + age + " is not possible.");
        }
        this.casualtyId = casualtyId;
        this.referenceNumber = referenceNumber.trim();
        this.name = trimToNull(name);
        this.age = age;
        this.contactNumber = trimToNull(contactNumber);
        this.triage = triage;
        this.location = trimToNull(location);
        this.notes = trimToNull(notes);
        this.status = CasualtyStatus.REGISTERED;
        this.registeredBy = registeredBy;
        this.registeredAt = LocalDateTime.now();
        this.lastUpdatedBy = registeredBy;
        this.lastUpdatedAt = this.registeredAt;
    }

    /** No-argument constructor required by Gson. */
    protected Casualty() {
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String getCasualtyId() {
        return casualtyId;
    }

    public void setCasualtyId(String casualtyId) {
        this.casualtyId = casualtyId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    /**
     * Their name, or null when they have not been identified.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    /**
     * What to call this casualty on a screen when no name is known.
     *
     * @return the name, or a description that identifies the record without
     *         inventing a name for the person
     */
    public String getDisplayName() {
        return name == null ? "Unidentified (" + casualtyId + ")" : name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = trimToNull(contactNumber);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = trimToNull(notes);
    }

    public TriageCategory getTriage() {
        return triage;
    }

    public void setTriage(TriageCategory triage) {
        this.triage = triage;
    }

    public CasualtyStatus getStatus() {
        return status;
    }

    public void setStatus(CasualtyStatus status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = trimToNull(location);
    }

    public String getReferredTo() {
        return referredTo;
    }

    public void setReferredTo(String referredTo) {
        this.referredTo = trimToNull(referredTo);
    }

    public LocalDateTime getReferredAt() {
        return referredAt;
    }

    public void setReferredAt(LocalDateTime referredAt) {
        this.referredAt = referredAt;
    }

    public String getRegisteredBy() {
        return registeredBy;
    }

    public void setRegisteredBy(String registeredBy) {
        this.registeredBy = registeredBy;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    /**
     * Changes the triage category, recording who changed it and when.
     *
     * A casualty's condition changes at a scene, and the record has to be able
     * to follow it. Who made the judgement is kept because a triage decision
     * is a clinical judgement somebody is accountable for.
     *
     * @param category the new category
     * @param changedBy who made the judgement
     */
    public void retriage(TriageCategory category, String changedBy) {
        if (category == null) {
            throw new IllegalArgumentException("A triage category is required.");
        }
        this.triage = category;
        touch(changedBy);
    }

    /**
     * Records that this casualty has been sent to a hospital.
     *
     * @param hospital   where they were sent
     * @param referredBy who sent them
     * @throws IllegalArgumentException when no hospital is named
     * @throws IllegalStateException    when they are recorded as deceased,
     *                                  because referring them would put a
     *                                  transport request into a hospital's
     *                                  queue ahead of living patients
     */
    public void referTo(String hospital, String referredBy) {
        String destination = trimToNull(hospital);
        if (destination == null) {
            throw new IllegalArgumentException("Name the hospital being referred to.");
        }
        if (triage == TriageCategory.DECEASED) {
            throw new IllegalStateException("A casualty triaged as deceased is not "
                    + "referred to a hospital. Change the triage category first if "
                    + "that category was recorded in error.");
        }
        this.referredTo = destination;
        this.referredAt = LocalDateTime.now();
        this.status = CasualtyStatus.REFERRED;
        touch(referredBy);
    }

    /**
     * Records a change of status that is not a referral.
     *
     * @param status    the new status
     * @param changedBy who changed it
     */
    public void updateStatus(CasualtyStatus status, String changedBy) {
        if (status == null) {
            throw new IllegalArgumentException("A status is required.");
        }
        if (status == CasualtyStatus.REFERRED && referredTo == null) {
            throw new IllegalArgumentException("Use the referral so that the "
                    + "hospital is recorded with it.");
        }
        this.status = status;
        touch(changedBy);
    }

    private void touch(String who) {
        this.lastUpdatedBy = who;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * A one line description for a list or an audit entry.
     *
     * Deliberately carries the identifier and the triage category and not the
     * name: these strings end up in the audit trail, which administrators can
     * read, and an administrator has no business reading a casualty's name.
     *
     * @return the description
     */
    public String describeWithoutIdentity() {
        return casualtyId + " (" + (triage == null ? "untriaged" : triage.getLabel())
                + ") on " + referenceNumber;
    }

    @Override
    public String toString() {
        return getDisplayName() + " - " + (triage == null ? "untriaged" : triage.getLabel())
                + (status == null ? "" : ", " + status.getLabel());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Casualty)) {
            return false;
        }
        return Objects.equals(casualtyId, ((Casualty) o).casualtyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(casualtyId);
    }
}
