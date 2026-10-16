package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.Casualty;
import com.cqu.drs.model.CasualtyStatus;
import com.cqu.drs.model.TriageCategory;
import com.cqu.drs.security.FieldCipher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores casualty records in MySQL, with the personal fields encrypted.
 *
 * <h2>Encrypt on the way in, decrypt on the way out, in one place</h2>
 * Every write passes through {@link #save} and every read through one of the
 * two finders, so there is no path to the table that skips the cipher. That is
 * the point of doing it here: a service can be added next to another service,
 * but nothing reaches this table except through this class.
 *
 * <p>The plaintext never touches the disk. It is encrypted into a local
 * variable, the ciphertext is bound to the statement, and what the database
 * receives is already unreadable. If encryption fails the write fails, rather
 * than falling back to storing the value plainly, because a fallback that
 * silently stores a casualty's name in the clear is worse than a failed save
 * that somebody notices.
 *
 * <h2>Records written before encryption was switched on</h2>
 * A value that is not in the stored form is returned as it is rather than
 * being reported as corrupt, so a database that predates this feature can
 * still be read. The condition is narrow and deliberate: it recognises the
 * stored form by its shape, so a tampered ciphertext still fails loudly
 * instead of being mistaken for old plaintext.
 */
public final class MySqlCasualtyRepository implements CasualtyRepository {

    private static final String UPSERT = """
            INSERT INTO casualties
              (casualty_id, reference_number, name_enc, contact_enc, notes_enc, age,
               triage, status, location, referred_to, referred_at,
               registered_by, registered_at, last_updated_by, last_updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE name_enc = VALUES(name_enc),
                                    contact_enc = VALUES(contact_enc),
                                    notes_enc = VALUES(notes_enc),
                                    age = VALUES(age),
                                    triage = VALUES(triage),
                                    status = VALUES(status),
                                    location = VALUES(location),
                                    referred_to = VALUES(referred_to),
                                    referred_at = VALUES(referred_at),
                                    last_updated_by = VALUES(last_updated_by),
                                    last_updated_at = VALUES(last_updated_at)""";

    private static final String SELECT = """
            SELECT casualty_id, reference_number, name_enc, contact_enc, notes_enc,
                   age, triage, status, location, referred_to, referred_at,
                   registered_by, registered_at, last_updated_by, last_updated_at
              FROM casualties""";

    /**
     * The ordering is written out rather than left to the stored values'
     * alphabetical order, which would put DECEASED first and IMMEDIATE third.
     *
     * It begins with a space on purpose. This is concatenated onto SELECT,
     * whose text block ends without one, and the two ran together into
     * "casualtiesWHERE" the first time it was written. The database reported
     * a syntax error pointing at the wrong place entirely, which is how that
     * class of mistake usually announces itself.
     */
    private static final String FOR_REPORT =
            " WHERE reference_number = ?"
            + " ORDER BY CASE triage"
            + "            WHEN 'IMMEDIATE' THEN 1"
            + "            WHEN 'DELAYED' THEN 2"
            + "            WHEN 'MINOR' THEN 3"
            + "            ELSE 4"
            + "          END, registered_at";

    /**
     * Counted in the database, so that producing the number never requires
     * decrypting a name.
     */
    private static final String COUNT_BY_TRIAGE = """
            SELECT triage, COUNT(*) AS total
              FROM casualties
             WHERE reference_number = ?
             GROUP BY triage""";

    private static final String COUNT_ALL = "SELECT COUNT(*) FROM casualties";

    private final ConnectionFactory connections;
    private final FieldCipher cipher;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     * @param cipher      the cipher the personal fields are protected with
     */
    public MySqlCasualtyRepository(ConnectionFactory connections, FieldCipher cipher) {
        if (connections == null || cipher == null) {
            throw new IllegalArgumentException(
                    "A casualty repository needs a connection factory and a cipher.");
        }
        this.connections = connections;
        this.cipher = cipher;
    }

    @Override
    public void save(Casualty casualty) {
        if (casualty == null) {
            throw new IllegalArgumentException("Cannot save a null casualty.");
        }

        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {

            statement.setString(1, casualty.getCasualtyId());
            statement.setString(2, casualty.getReferenceNumber());

            // The three personal fields, encrypted before they are bound. What
            // crosses to the database is already unreadable.
            statement.setString(3, cipher.encrypt(casualty.getName()));
            statement.setString(4, cipher.encrypt(casualty.getContactNumber()));
            statement.setString(5, cipher.encrypt(casualty.getNotes()));

            if (casualty.getAge() == null) {
                statement.setNull(6, Types.INTEGER);
            } else {
                statement.setInt(6, casualty.getAge());
            }

            statement.setString(7, casualty.getTriage().name());
            statement.setString(8, casualty.getStatus().name());
            statement.setString(9, casualty.getLocation());
            statement.setString(10, casualty.getReferredTo());
            statement.setObject(11, truncate(casualty.getReferredAt()));
            statement.setString(12, casualty.getRegisteredBy());
            statement.setObject(13, truncate(casualty.getRegisteredAt()));
            statement.setString(14, casualty.getLastUpdatedBy());
            statement.setObject(15, truncate(casualty.getLastUpdatedAt()));

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException("The casualty record could not be saved. "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Casualty> findById(String casualtyId) {
        if (casualtyId == null) {
            return Optional.empty();
        }
        List<Casualty> found = read(SELECT + " WHERE casualty_id = ?", casualtyId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * {@inheritDoc}
     *
     * Ordered by the triage rank in SQL rather than after loading, so the
     * most urgent casualty is first even when only the first page of a long
     * list is looked at. The order is written out explicitly rather than
     * relying on the alphabetical order of the stored values, which would put
     * DECEASED first and IMMEDIATE third.
     */
    @Override
    public List<Casualty> findForReport(String reference) {
        return read(SELECT + FOR_REPORT, reference);
    }

    @Override
    public Map<TriageCategory, Integer> countByTriage(String reference) {
        Map<TriageCategory, Integer> counts = new EnumMap<>(TriageCategory.class);
        for (TriageCategory category : TriageCategory.values()) {
            // Every category present, including the empty ones. A summary that
            // omitted "Immediate: 0" would be read as the figure not having
            // been collected rather than as there being none.
            counts.put(category, 0);
        }

        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(COUNT_BY_TRIAGE)) {

            statement.setString(1, reference);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    counts.put(TriageCategory.valueOf(results.getString("triage")),
                            results.getInt("total"));
                }
            }
            return counts;

        } catch (SQLException e) {
            throw new IllegalStateException("The casualty summary could not be read. "
                    + e.getMessage(), e);
        }
    }

    @Override
    public int count() {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(COUNT_ALL)) {
            return results.next() ? results.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("The casualties could not be counted. "
                    + e.getMessage(), e);
        }
    }

    private List<Casualty> read(String sql, String parameter) {
        List<Casualty> casualties = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, parameter);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    casualties.add(readCasualty(results));
                }
            }
            return casualties;

        } catch (SQLException e) {
            throw new IllegalStateException("The casualty records could not be read. "
                    + e.getMessage(), e);
        }
    }

    private Casualty readCasualty(ResultSet results) throws SQLException {
        Casualty casualty = new Casualty(results.getString("casualty_id"),
                results.getString("reference_number"),
                decrypt(results.getString("name_enc")),
                (Integer) results.getObject("age"),
                decrypt(results.getString("contact_enc")),
                TriageCategory.valueOf(results.getString("triage")),
                results.getString("location"),
                decrypt(results.getString("notes_enc")),
                results.getString("registered_by"));

        // The constructor stamps the current time and sets the status to
        // registered, both of which describe a new record rather than a stored
        // one.
        casualty.setStatus(CasualtyStatus.valueOf(results.getString("status")));
        casualty.setReferredTo(results.getString("referred_to"));
        casualty.setReferredAt(results.getObject("referred_at", LocalDateTime.class));
        casualty.setRegisteredAt(
                results.getObject("registered_at", LocalDateTime.class));
        casualty.setLastUpdatedBy(results.getString("last_updated_by"));
        casualty.setLastUpdatedAt(
                results.getObject("last_updated_at", LocalDateTime.class));
        return casualty;
    }

    /**
     * Decrypts a stored value, allowing for records written before encryption
     * was switched on.
     *
     * The shape test is what keeps this honest. A value that is not in the
     * stored form at all is old plaintext and is returned; a value that is in
     * the stored form and fails to decrypt has been altered, and that failure
     * is allowed through rather than swallowed.
     *
     * @param stored the column value
     * @return the plain value
     */
    private String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!FieldCipher.looksEncrypted(stored)) {
            return stored;
        }
        return cipher.decrypt(stored);
    }

    private static LocalDateTime truncate(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }
}
