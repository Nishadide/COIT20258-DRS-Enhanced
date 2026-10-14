package com.cqu.drs.repository;

import com.cqu.drs.model.User;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JSON-file implementation. Stores staff accounts. The three concrete roles are written and read back
 * through the adapter registered on the store, using the role already held on
 * each user to decide which class to build.
 */
public class JsonUserRepository implements UserRepository {

    private static final String FILE_NAME = "users.json";
    private static final Type LIST_TYPE = new TypeToken<ArrayList<User>>() { }.getType();

    private final JsonDataStore store;

    /**
     * Creates a repository backed by the store given.
     *
     * @param store the data store to read and write through
     */
    public JsonUserRepository(JsonDataStore store) {
        this.store = store;
    }

    /**
     * Every account held.
     *
     * @return the users, or an empty list when none are held
     */
    @Override
    public List<User> findAll() {
        return store.load(FILE_NAME, LIST_TYPE);
    }

    /**
     * Finds one account by its login name. The comparison ignores case, so a
     * user cannot be locked out by the shift key.
     *
     * @param username the login name to look for
     * @return the user, or an empty Optional when no account has that name
     */
    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    /**
     * Reports whether a login name is already taken.
     *
     * @param username the name to check
     * @return true when an account already uses it
     */
    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    /**
     * Saves an account, replacing any earlier version of the same account.
     *
     * @param user the account to save
     */
    @Override
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cannot save a null user.");
        }
        List<User> all = findAll();
        all.removeIf(u -> user.getUserId().equals(u.getUserId()));
        all.add(user);
        store.store(FILE_NAME, all);
    }
}
