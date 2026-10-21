package de.skyfunity.weihnachten;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verwaltet den Adventskalender: welche Tage ein Spieler schon abgeholt hat
 * und ob der Bonus fuer alle 24 Tage bereits vergeben wurde.
 */
public class AdventCalendarManager {
    private final WeihnachtsPlugin plugin;
    private final File file;
    private final Map<UUID, Set<Integer>> claimed = new HashMap<>();
    private final Map<UUID, Boolean> bonus = new HashMap<>();

    public AdventCalendarManager(WeihnachtsPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "advent.yml");
        load();
    }

    public Set<Integer> claimed(UUID u) { return claimed.computeIfAbsent(u, x -> new HashSet<>()); }
    public boolean hasClaimed(UUID u, int day) { return claimed(u).contains(day); }
    public void claim(UUID u, int day) { claimed(u).add(day); save(); }
    public boolean bonusClaimed(UUID u) { return bonus.getOrDefault(u, false); }
    public void setBonus(UUID u) { bonus.put(u, true); save(); }
    public boolean allClaimed(UUID u) { return claimed(u).size() >= 24; }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        if (c.getConfigurationSection("players") == null) return;
        for (String k : c.getConfigurationSection("players").getKeys(false)) {
            UUID u = UUID.fromString(k);
            claimed.put(u, new HashSet<>(c.getIntegerList("players." + k + ".claimed")));
            bonus.put(u, c.getBoolean("players." + k + ".bonus", false));
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (UUID u : claimed.keySet()) {
            c.set("players." + u + ".claimed", new java.util.ArrayList<>(claimed.get(u)));
            c.set("players." + u + ".bonus", bonus.getOrDefault(u, false));
        }
        try { c.save(file); } catch (IOException ignored) {}
    }
}