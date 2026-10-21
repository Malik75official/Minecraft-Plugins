package de.skyfunity.weihnachten;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Speichert und verwaltet die Cookie-Währung der Spieler.
 * Die Daten werden in cookies.yml abgelegt.
 */
public class CookieManager {
    private final WeihnachtsPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> cookies = new HashMap<>();

    public CookieManager(WeihnachtsPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "cookies.yml");
        load();
    }

    public int get(UUID uuid) {
        return cookies.getOrDefault(uuid, 0);
    }

    public void add(UUID uuid, int amount) {
        cookies.put(uuid, get(uuid) + amount);
        save();
    }

    public boolean take(UUID uuid, int amount) {
        int cur = get(uuid);
        if (cur < amount)
            return false;
        cookies.put(uuid, cur - amount);
        save();
        return true;
    }

    private void load() {
        if (!file.exists())
            return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        if (c.getConfigurationSection("cookies") == null)
            return;
        for (String k : c.getConfigurationSection("cookies").getKeys(false)) {
            cookies.put(UUID.fromString(k), c.getInt("cookies." + k));
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : cookies.entrySet()) {
            c.set("cookies." + e.getKey(), e.getValue());
        }
        try {
            c.save(file);
        } catch (IOException ignored) {
        }
    }
}