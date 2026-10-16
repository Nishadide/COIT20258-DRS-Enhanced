package com.cqu.drs.repository;

import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.security.FieldCipher;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores casualty records in a JSON file, for the local mode the application
 * runs in when no server is started.
 *
 * <h2>The file is encrypted too</h2>
 * It would have been easier to leave local mode in plain text, on the grounds
 * that it is only for development. That is exactly the reasoning that puts
 * real data in a readable file: local mode is what runs during a
 * demonstration, and a casualty's name sitting in plain text in
 * {@code casualties.json} is the same exposure whether or not the mode was
 * meant for development.
 *
 * <p>So the same cipher is applied, with the same key, through the same
 * methods. The two stores differ in where the bytes land and in nothing else.
 */
public class JsonCasualtyRepository implements CasualtyRepository {

    private static final String FILE_NAME = "casualties.json";

    private static final Type LIST_TYPE =
            new TypeToken<ArrayList<Casualty>>() { }.getType();

    private final JsonDataStore store;
    private final FieldCipher cipher;

    /**
     * Creates the repository.
     *
     * @param store  where the file is written
     * @param cipher the cipher the personal fields are protected with
     */
    public JsonCasualtyRepository(JsonDataStore store, FieldCipher cipher) {
        if (store == null || cipher == null) {
            throw new IllegalArgumentException(
                    "A casualty repository needs a data store and a cipher.");
        }
        this.store = store;
        this.cipher = cipher;
    }

    @Override
    public synchronized void save(Casualty casualty) {
        if (casualty == null) {
            throw new IllegalArgumentException("Cannot save a null casualty.");
        }
        List<Casualty> all = store.load(FILE_NAME, LIST_TYPE);
        all.removeIf(held -> held.getCasualtyId().equals(casualty.getCasualtyId()));
        all.add(encrypted(casualty));
        store.store(FILE_NAME, all);
    }

    @Override
    public synchronized Optional<Casualty> findById(String casualtyId) {
        if (casualtyId == null) {
            return Optional.empty();
        }
        return store.<Casualty>load(FILE_NAME, LIST_TYPE).stream()
                .filter(casualty -> casualtyId.equals(casualty.getCasualtyId()))
                .map(this::decrypted)
                .findFirst();
    }

    @Override
    public synchronized List<Casualty> findForReport(String reference) {
        List<Casualty> matching = new ArrayList<>();
        for (Casualty casualty : store.<Casualty>load(FILE_NAME, LIST_TYPE)) {
            if (casualty.getReferenceNumber() != null
                    && casualty.getReferenceNumber().equals(reference)) {
                matching.add(decrypted(casualty));
            }
        }
        matching.sort(Comparator
                .comparingInt((Casualty casualty) -> casualty.getTriage() == null
                        ? Integer.MAX_VALUE : casualty.getTriage().getRank())
                .thenComparing(Casualty::getRegisteredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return matching;
    }

    @Override
    public synchronized Map<TriageCategory, Integer> countByTriage(String reference) {
        Map<TriageCategory, Integer> counts = new EnumMap<>(TriageCategory.class);
        for (TriageCategory category : TriageCategory.values()) {
            counts.put(category, 0);
        }
        // Counted from the stored records without decrypting anything: the
        // triage category is not an encrypted field, so a summary never
        // touches a name.
        for (Casualty casualty : store.<Casualty>load(FILE_NAME, LIST_TYPE)) {
            if (casualty.getReferenceNumber() != null
                    && casualty.getReferenceNumber().equals(reference)
                    && casualty.getTriage() != null) {
                counts.merge(casualty.getTriage(), 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public synchronized int count() {
        return store.load(FILE_NAME, LIST_TYPE).size();
    }

    /**
     * A copy of the record with its personal fields encrypted, for writing.
     *
     * A copy rather than the object itself, because the caller still holds it
     * and would find its name replaced by ciphertext.
     *
     * @param casualty the record
     * @return the copy to store
     */
    private Casualty encrypted(Casualty casualty) {
        Casualty copy = copyOf(casualty);
        copy.setName(cipher.encrypt(casualty.getName()));
        copy.setContactNumber(cipher.encrypt(casualty.getContactNumber()));
        copy.setNotes(cipher.encrypt(casualty.getNotes()));
        return copy;
    }

    /**
     * A copy of a stored record with its personal fields decrypted.
     *
     * @param stored the record as it was read
     * @return the usable record
     */
    private Casualty decrypted(Casualty stored) {
        Casualty copy = copyOf(stored);
        copy.setName(decrypt(stored.getName()));
        copy.setContactNumber(decrypt(stored.getContactNumber()));
        copy.setNotes(decrypt(stored.getNotes()));
        return copy;
    }

    private String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        return FieldCipher.looksEncrypted(stored) ? cipher.decrypt(stored) : stored;
    }

    private static Casualty copyOf(Casualty source) {
        Casualty copy = new Casualty(source.getCasualtyId(), source.getReferenceNumber(),
                source.getName(), source.getAge(), source.getContactNumber(),
                source.getTriage(), source.getLocation(), source.getNotes(),
                source.getRegisteredBy());
        copy.setStatus(source.getStatus());
        copy.setReferredTo(source.getReferredTo());
        copy.setReferredAt(source.getReferredAt());
        copy.setRegisteredAt(source.getRegisteredAt());
        copy.setLastUpdatedBy(source.getLastUpdatedBy());
        copy.setLastUpdatedAt(source.getLastUpdatedAt());
        return copy;
    }
}
