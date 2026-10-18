package dev.codex.compressedblocks.token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads and, when necessary, creates the secret used to authenticate tokens. */
public final class SecretKeyManager {
    public static final int KEY_LENGTH_BYTES = 32;

    private static final String FILE_NAME = "secret.key";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecretKeyManager() {
    }

    /**
     * Loads the plugin's key or creates it atomically in the plugin data directory.
     *
     * @param plugin owning plugin
     * @return a new byte array containing the 256-bit key
     * @throws IOException if the key cannot be read, validated, or created
     */
    public static byte[] loadOrCreate(JavaPlugin plugin) throws IOException {
        Objects.requireNonNull(plugin, "plugin");
        return loadOrCreate(plugin.getDataFolder().toPath());
    }

    /**
     * Loads the key from {@code dataDirectory/secret.key}, creating it if absent.
     * Existing malformed keys are rejected so that valid tokens are never silently
     * invalidated by replacing their signing key.
     *
     * @param dataDirectory plugin data directory
     * @return a new byte array containing the 256-bit key
     * @throws IOException if the key cannot be read, validated, or created
     */
    public static synchronized byte[] loadOrCreate(Path dataDirectory) throws IOException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Files.createDirectories(dataDirectory);

        Path keyFile = dataDirectory.resolve(FILE_NAME);
        if (Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            return readKey(keyFile);
        }

        byte[] generated = new byte[KEY_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(generated);
        String encoded = Base64.getEncoder().encodeToString(generated) + '\n';
        Path temporary = Files.createTempFile(dataDirectory, ".secret-key-", ".tmp");

        try {
            Files.writeString(
                    temporary,
                    encoded,
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(temporary, keyFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, keyFile);
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                return readKey(keyFile);
            }

            return generated.clone();
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return readKey(keyFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] readKey(Path keyFile) throws IOException {
        if (!Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Die Schlüsseldatei ist keine reguläre Datei: " + keyFile);
        }

        String encoded = Files.readString(keyFile, StandardCharsets.US_ASCII).strip();
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Die Schlüsseldatei enthält kein gültiges Base64.", exception);
        }

        if (key.length != KEY_LENGTH_BYTES) {
            throw new IOException(
                    "Die Schlüsseldatei muss genau " + KEY_LENGTH_BYTES + " Byte enthalten.");
        }
        return key;
    }
}
