package com.cqu.drs.security;

import com.cqu.drs.db.DatabaseConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Finds the key the field encryption uses, and refuses to invent one quietly.
 *
 * <h2>Where the key comes from, in order</h2>
 * <ol>
 *   <li>{@code drs.security.key}, a system property or the environment
 *       variable {@code DRS_SECURITY_KEY}, holding the key base64 encoded.
 *       This is how a deployed system would supply it, from whatever manages
 *       its secrets.</li>
 *   <li>A file named by {@code drs.security.keyFile}, default
 *       {@code drs-encryption.key} beside the application.</li>
 *   <li>Failing both, a key is generated and written to that file, once, and
 *       the fact is announced loudly on the console.</li>
 * </ol>
 *
 * <h2>Why generating one is acceptable here and would not be in production</h2>
 * Without a key the casualty registry cannot run at all, and a prototype that
 * refuses to start until somebody runs a key generation tool is a prototype
 * nobody can demonstrate. So one is generated, written where it will be found
 * again, and excluded from the repository by {@code .gitignore} so that it is
 * never committed.
 *
 * <p>What is not acceptable, and is the reason this class exists rather than a
 * constant somewhere, is a key compiled into the source. A key in the source
 * is a key in the repository, in every clone of it, and in the submitted
 * archive. Anyone holding the code could then read every encrypted record,
 * which would make the encryption a gesture rather than a control.
 *
 * <p>The limitation that remains is honest and recorded in the report: the key
 * sits in a file on the same machine as the database, so it protects against
 * someone reading the database alone, not against someone who has the whole
 * host. A deployed system would hold it in a managed key store.
 */
public final class EncryptionKeys {

    /** The property naming the key directly, base64 encoded. */
    public static final String KEY_PROPERTY = "drs.security.key";

    /** The property naming the file the key is kept in. */
    public static final String KEY_FILE_PROPERTY = "drs.security.keyFile";

    private static final String DEFAULT_KEY_FILE = "drs-encryption.key";

    /** Not instantiable: this class holds static lookups only. */
    private EncryptionKeys() {
    }

    /**
     * The cipher the application should use.
     *
     * @param config where the settings are read from
     * @return a cipher holding the key that was found or created
     */
    public static FieldCipher cipher(DatabaseConfig config) {
        return new FieldCipher(key(config));
    }

    /**
     * The key itself.
     *
     * @param config where the settings are read from
     * @return 32 bytes of key
     */
    public static byte[] key(DatabaseConfig config) {
        String configured = config.get(KEY_PROPERTY, null);
        if (configured != null && !configured.isBlank()) {
            return decode(configured, "the " + KEY_PROPERTY + " setting");
        }

        Path keyFile = Path.of(config.get(KEY_FILE_PROPERTY, DEFAULT_KEY_FILE));
        if (Files.exists(keyFile)) {
            try {
                return decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim(),
                        keyFile.toAbsolutePath().toString());
            } catch (IOException e) {
                throw new IllegalStateException("The encryption key file "
                        + keyFile.toAbsolutePath() + " could not be read. "
                        + e.getMessage(), e);
            }
        }

        return createKeyFile(keyFile);
    }

    private static byte[] createKeyFile(Path keyFile) {
        byte[] key = FieldCipher.newKey();
        try {
            Path parent = keyFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(keyFile,
                    Base64.getEncoder().encodeToString(key), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException("No encryption key was configured and one "
                    + "could not be written to " + keyFile.toAbsolutePath()
                    + ". Set " + KEY_PROPERTY + " instead. " + e.getMessage(), e);
        }

        // Announced rather than done silently. Somebody who did not realise a
        // key was missing needs to know that one now exists, that the records
        // encrypted from here on can only be read with it, and that losing the
        // file loses the records.
        System.out.println("A new encryption key was generated and written to "
                + keyFile.toAbsolutePath());
        System.out.println("Keep it. Casualty records encrypted with it cannot be "
                + "read without it, and it is excluded from version control.");
        return key;
    }

    private static byte[] decode(String base64, String where) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The encryption key in " + where
                    + " is not valid base64.", e);
        }
        if (key.length != 32) {
            throw new IllegalStateException("The encryption key in " + where
                    + " is " + key.length + " bytes. AES-256 needs exactly 32.");
        }
        return key;
    }
}
