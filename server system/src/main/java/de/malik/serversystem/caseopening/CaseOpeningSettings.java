package de.malik.serversystem.caseopening;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import de.malik.serversystem.ServerSystemBootstrap;
import de.malik.serversystem.storage.PersistentYamlStorage;

public final class CaseOpeningSettings {
    private static final String FILE_NAME = "caseopening.yml";

    private final ServerSystemBootstrap plugin;
    private final PersistentYamlStorage storage;
    private final File file;
    private FileConfiguration data;

    public CaseOpeningSettings(ServerSystemBootstrap plugin, PersistentYamlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        boolean databaseHasData = storage.hasData(FILE_NAME);
        boolean fileExisted = file.isFile();
        if (!databaseHasData && !fileExisted) {
            plugin.saveResource(FILE_NAME, false);
        }
        data = storage.load(FILE_NAME, file);
        if (!databaseHasData && !fileExisted) {
            migrateCases(new File(plugin.getDataFolder().getParentFile(), "CaseOpening-v26.2/config.yml"));
            migrateCases(new File(plugin.getDataFolder(), "config.yml"));
            save();
        }
    }

    public FileConfiguration getData() {
        return data;
    }

    public void reload() {
        data = storage.load(FILE_NAME, file);
    }

    public void save() {
        try {
            if (!storage.isMysqlEnabled() && file.isFile()) {
                File backup = new File(plugin.getDataFolder(), FILE_NAME + ".bak");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            storage.save(FILE_NAME, data, file);
        } catch (IOException exception) {
            plugin.getLogger().severe("caseopening.yml konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    public boolean isGenericEnderChest(Location location) {
        return location != null && data.getStringList("generic-enderchests").contains(locationKey(location));
    }

    public void setGenericEnderChest(Location location, boolean enabled) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        List<String> locations = new ArrayList<>(data.getStringList("generic-enderchests"));
        String key = locationKey(location);
        locations.removeIf(key::equals);
        if (enabled) {
            locations.add(key);
        }
        data.set("generic-enderchests", locations);
        save();
    }

    private void migrateCases(File sourceFile) {
        if (!sourceFile.isFile()) {
            return;
        }
        FileConfiguration source = YamlConfiguration.loadConfiguration(sourceFile);
        ConfigurationSection cases = source.getConfigurationSection("cases");
        if (cases == null) {
            return;
        }
        for (String caseId : cases.getKeys(false)) {
            ConfigurationSection caseSection = cases.getConfigurationSection(caseId);
            if (caseSection != null) {
                data.set("cases." + caseId, caseSection.getValues(false));
            }
        }
        plugin.getLogger().info("Case-Definitionen aus " + sourceFile.getName() + " übernommen.");
    }

    private String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }
}