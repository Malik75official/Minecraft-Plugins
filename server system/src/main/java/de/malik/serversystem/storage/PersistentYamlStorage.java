package de.malik.serversystem.storage;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mariadb.jdbc.Driver;

public final class PersistentYamlStorage {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,48}");

    private final JavaPlugin plugin;
    private final boolean mysqlEnabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String tableName;

    public PersistentYamlStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        mysqlEnabled = plugin.getConfig().getBoolean("storage.mysql.enabled", false);
        jdbcUrl = plugin.getConfig().getString("storage.mysql.jdbc-url", "jdbc:mariadb://localhost:3306/serversystem");
        username = plugin.getConfig().getString("storage.mysql.username", "serversystem");
        password = plugin.getConfig().getString("storage.mysql.password", "CHANGE_ME");
        tableName = plugin.getConfig().getString("storage.mysql.table", "serversystem_plugin_data");
        if (!SQL_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("storage.mysql.table darf nur Buchstaben, Zahlen und Unterstriche enthalten.");
        }
    }

    public boolean isMysqlEnabled() {
        return mysqlEnabled;
    }

    public void initialize() throws SQLException {
        if (!mysqlEnabled) {
            return;
        }
        String createTable = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "data_key VARCHAR(191) NOT NULL PRIMARY KEY, "
                + "yaml_data LONGTEXT NOT NULL, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }
    }

    public boolean hasData(String key) {
        if (!mysqlEnabled) {
            return false;
        }
        String query = "SELECT 1 FROM " + tableName + " WHERE data_key = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw storageFailure("Datenbankeintrag '" + key + "' konnte nicht geprüft werden", exception);
        }
    }

    public FileConfiguration load(String key, File file) {
        return load(key, file, null);
    }

    public FileConfiguration load(String key, File file, File fallbackFile) {
        if (mysqlEnabled) {
            String query = "SELECT yaml_data FROM " + tableName + " WHERE data_key = ?";
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, key);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return YamlConfiguration.loadConfiguration(new StringReader(result.getString(1)));
                    }
                }
            } catch (SQLException exception) {
                throw storageFailure("Datenbankeintrag '" + key + "' konnte nicht geladen werden", exception);
            }
        }

        File source = file.isFile() ? file : fallbackFile;
        FileConfiguration data = source != null && source.isFile()
                ? YamlConfiguration.loadConfiguration(source)
                : new YamlConfiguration();
        if (mysqlEnabled) {
            save(key, data, file);
        }
        return data;
    }

    public void save(String key, FileConfiguration data, File file) {
        if (mysqlEnabled) {
            String upsert = "INSERT INTO " + tableName + " (data_key, yaml_data) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE yaml_data = VALUES(yaml_data), updated_at = CURRENT_TIMESTAMP";
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, key);
                statement.setString(2, data.saveToString());
                statement.executeUpdate();
                return;
            } catch (SQLException exception) {
                throw storageFailure("Datenbankeintrag '" + key + "' konnte nicht gespeichert werden", exception);
            }
        }

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Plugin-Datenordner konnte nicht erstellt werden.");
            }
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Datei " + file.getName() + " konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private Connection openConnection() throws SQLException {
    Driver driver = new Driver();

    java.util.Properties properties = new java.util.Properties();
    properties.setProperty("user", username);
    properties.setProperty("password", password);

    Connection connection = driver.connect(jdbcUrl, properties);

    if (connection == null) {
        throw new SQLException(
            "MariaDB-Treiber akzeptiert die JDBC-URL nicht: " + jdbcUrl
        );
    }

    return connection;
}

    private IllegalStateException storageFailure(String message, SQLException cause) {
        plugin.getLogger().severe(message + ": " + cause.getMessage());
        return new IllegalStateException(message, cause);
    }
}