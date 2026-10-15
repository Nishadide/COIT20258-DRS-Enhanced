package com.cqu.drs.repository;

import com.cqu.drs.db.ConnectionFactory;
import com.cqu.drs.model.DepartmentResponder;
import com.cqu.drs.model.EmergencyCoordinator;
import com.cqu.drs.model.SystemAdministrator;
import com.cqu.drs.model.User;
import com.cqu.drs.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores user accounts in MySQL.
 *
 * <h2>One table for three kinds of user</h2>
 * The User hierarchy is mapped with single table inheritance and the role
 * column as the discriminator. The two subclasses that carry state add one
 * field each, department_id and coordinator_id, and SystemAdministrator adds
 * none, so a table per subclass would have meant three extra tables and a join
 * on every lookup to store two nullable columns.
 *
 * <p>What that costs is the ability to require a responder's department with a
 * NOT NULL, since the column must be nullable for the other two roles. The
 * schema gets it back with a check constraint permitting a null department
 * only when the role is not RESPONDER, so the rule AccountService enforces in
 * Java is enforced again in the database.
 *
 * <h2>Rebuilding a user without their password</h2>
 * A user read from a row has a stored hash and no plaintext, but every
 * constructor in the hierarchy takes a plaintext password, because the model
 * was written for a prototype where Gson bypassed constructors entirely.
 * Rather than add a constructor to the model for the benefit of one storage
 * technology, a user is built with a placeholder and the stored hash is put in
 * immediately afterwards.
 *
 * <p>The placeholder never leaves this class, is never stored, and is
 * overwritten before the object is returned. It exists only to satisfy the
 * validation on the way past.
 */
public final class MySqlUserRepository implements UserRepository {

    /**
     * Long enough to pass the password rules, and discarded immediately.
     *
     * Its value is irrelevant: it is hashed by the constructor and that hash
     * is replaced with the stored one on the next line.
     */
    private static final String PLACEHOLDER_PASSWORD = "placeholder-replaced-at-once";

    private static final String COLUMNS = """
            user_id, username, password_hash, password_salt, password_algorithm,
            full_name, role, active, must_change_password, department_id, coordinator_id""";

    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM users ORDER BY username";

    private static final String SELECT_BY_USERNAME =
            "SELECT " + COLUMNS + " FROM users WHERE username = ?";

    private static final String EXISTS =
            "SELECT 1 FROM users WHERE username = ?";

    private static final String EXISTS_BY_ID =
            "SELECT 1 FROM users WHERE user_id = ?";

    private static final String INSERT = """
            INSERT INTO users
              (user_id, username, password_hash, password_salt, password_algorithm,
               full_name, role, active, must_change_password, department_id, coordinator_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String UPDATE = """
            UPDATE users
               SET username = ?, password_hash = ?, password_salt = ?,
                   password_algorithm = ?, full_name = ?, role = ?, active = ?,
                   must_change_password = ?, department_id = ?, coordinator_id = ?
             WHERE user_id = ?""";

    private final ConnectionFactory connections;

    /**
     * Creates the repository.
     *
     * @param connections where connections come from
     */
    public MySqlUserRepository(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("A repository needs a connection factory.");
        }
        this.connections = connections;
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(SELECT_ALL)) {

            while (results.next()) {
                users.add(readUser(results));
            }
            return users;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "The user accounts could not be read. " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * The match ignores case, because the column's collation is accent and
     * case insensitive. That is deliberate rather than incidental: Assessment
     * 2 matched usernames without regard to case and has a test proving it, so
     * the database was given a collation that behaves the same way instead of
     * the behaviour changing when the storage did.
     */
    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_USERNAME)) {

            statement.setString(1, username.trim());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readUser(results)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("The account " + username
                    + " could not be read. " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(EXISTS)) {

            statement.setString(1, username.trim());
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("The account " + username
                    + " could not be looked up. " + e.getMessage(), e);
        }
    }

    @Override
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cannot save a null user.");
        }

        try (Connection connection = connections.open()) {
            boolean stored = exists(connection, user.getUserId());
            String sql = stored ? UPDATE : INSERT;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                if (!stored) {
                    statement.setString(i++, user.getUserId());
                }
                statement.setString(i++, user.getUsername());
                statement.setString(i++, user.getPasswordHash());

                // Both are written from what the model holds today, which is
                // an unsalted digest. The columns exist so that the change to
                // salted PBKDF2 needs no change of schema, and so that a
                // digest carried over from Assessment 2 can be recognised by
                // its algorithm and upgraded the next time its owner signs in.
                statement.setString(i++, null);
                statement.setString(i++, "SHA-256");

                statement.setString(i++, user.getFullName());
                statement.setString(i++, user.getRole().name());
                statement.setBoolean(i++, user.isActive());
                statement.setBoolean(i++, user.isMustChangePassword());
                statement.setString(i++, departmentOf(user));
                statement.setString(i++, coordinatorOf(user));
                if (stored) {
                    statement.setString(i, user.getUserId());
                }

                statement.executeUpdate();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("The account " + user.getUsername()
                    + " could not be stored. " + e.getMessage(), e);
        }
    }

    private boolean exists(Connection connection, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS_BY_ID)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static String departmentOf(User user) {
        return user instanceof DepartmentResponder responder
                ? responder.getDepartmentId() : null;
    }

    private static String coordinatorOf(User user) {
        return user instanceof EmergencyCoordinator coordinator
                ? coordinator.getCoordinatorId() : null;
    }

    /**
     * Builds the right kind of user for the role the row carries.
     *
     * @param results positioned on a user row
     * @return the user
     * @throws SQLException when a column cannot be read
     */
    private static User readUser(ResultSet results) throws SQLException {
        String userId = results.getString("user_id");
        String username = results.getString("username");
        String fullName = results.getString("full_name");
        UserRole role = UserRole.valueOf(results.getString("role"));

        User user = switch (role) {
            case COORDINATOR -> new EmergencyCoordinator(userId, username,
                    PLACEHOLDER_PASSWORD, fullName, results.getString("coordinator_id"));
            case RESPONDER -> new DepartmentResponder(userId, username,
                    PLACEHOLDER_PASSWORD, fullName, results.getString("department_id"));
            case ADMINISTRATOR -> new SystemAdministrator(userId, username,
                    PLACEHOLDER_PASSWORD, fullName);
        };

        // The real hash, replacing the one the constructor made from the
        // placeholder. Until this line the object cannot authenticate anyone.
        user.setPasswordHash(results.getString("password_hash"));
        user.setActive(results.getBoolean("active"));
        user.setMustChangePassword(results.getBoolean("must_change_password"));
        return user;
    }
}
