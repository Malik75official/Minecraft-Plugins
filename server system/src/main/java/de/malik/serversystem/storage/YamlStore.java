package de.malik.serversystem.storage;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlStore {
    private final JavaPlugin plugin;
    private final File file;
    private final String key;
    private final PersistentYamlStorage storage;
    private final FileConfiguration data;

    public YamlStore(JavaPlugin plugin, String name, PersistentYamlStorage storage) {
        this.plugin = plugin;
        this.key = name;
        this.storage = storage;
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Plugin-Datenordner konnte nicht erstellt werden.");
        }
        file = new File(folder, name);
        data = storage.load(key, file);
    }

    public FileConfiguration data() {
        return data;
    }

    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    public void save() {
        storage.save(key, data, file);
    }
}