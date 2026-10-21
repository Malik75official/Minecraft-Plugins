package de.skyfunity.weihnachten;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HeadManager {
    private final WeihnachtsPlugin plugin;
    private final File file;
    private final Map<String, Integer> heads = new HashMap<>(); // key -> cookies
    private final Map<String, Set<UUID>> claimed = new HashMap<>();

    public HeadManager(WeihnachtsPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "heads.yml");
        load();
    }

    public String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public boolean isHead(Location loc) { return heads.containsKey(key(loc)); }
    public int amount(Location loc) { return heads.getOrDefault(key(loc), 0); }

    public void register(Location loc, int amount) {
        heads.put(key(loc), amount);
        claimed.putIfAbsent(key(loc), new HashSet<>());
        save();
    }

    public boolean claim(UUID uuid, Location loc) {
        String k = key(loc);
        Set<UUID> set = claimed.computeIfAbsent(k, x -> new HashSet<>());
        if (set.contains(uuid)) return false;
        set.add(uuid);
        save();
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        if (c.getConfigurationSection("heads") == null) return;
        for (String k : c.getConfigurationSection("heads").getKeys(false)) {
            heads.put(k, c.getInt("heads." + k + ".amount", 1));
            claimed.put(k, new HashSet<>());
            for (String u : c.getStringList("heads." + k + ".claimed")) {
                claimed.get(k).add(UUID.fromString(u));
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<String, Integer> e : heads.entrySet()) {
            String k = e.getKey();
            c.set("heads." + k + ".amount", e.getValue());
            java.util.List<String> list = new java.util.ArrayList<>();
            for (UUID u : claimed.getOrDefault(k, new HashSet<>())) list.add(u.toString());
            c.set("heads." + k + ".claimed", list);
        }
        try { c.save(file); } catch (IOException ignored) {}
    }
}