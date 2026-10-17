package de.malik.serversystem.storage;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlStore {
    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public YamlStore(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Plugin-Datenordner konnte nicht erstellt werden.");
        }
        file = new File(folder, name);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("Datei " + name + " konnte nicht erstellt werden: " + exception.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration data() {
        return data;
    }

    public void logWarning(String message) {
        plugin.getLogger().warning(message);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Datei " + file.getName() + " konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }
}