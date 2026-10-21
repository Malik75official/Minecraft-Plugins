package de.skyfunity.weihnachten;

import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet den Cookie-Shop: Items und Preise pro Tag.
 * Daten werden in shop.yml gespeichert.
 */
public class ShopManager {
    private final WeihnachtsPlugin plugin;
    private final File file;
    private final Map<Integer, List<ShopEntry>> items = new HashMap<>();

    public static class ShopEntry {
        public ItemStack item;
        public int price;
        public ShopEntry(ItemStack item, int price) { this.item = item; this.price = price; }
    }

    public ShopManager(WeihnachtsPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        load();
    }

    public List<ShopEntry> forDay(int day) { return items.getOrDefault(day, new ArrayList<>()); }
    public void setDay(int day, List<ShopEntry> list) { items.put(day, list); save(); }
    public Map<Integer, List<ShopEntry>> all() { return items; }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        if (c.getConfigurationSection("days") == null) return;
        for (String d : c.getConfigurationSection("days").getKeys(false)) {
            int day;
            try { day = Integer.parseInt(d); } catch (NumberFormatException e) { continue; }
            List<ShopEntry> list = new ArrayList<>();
            List<Map<?, ?>> raw = c.getMapList("days." + d);
            for (Map<?, ?> m : raw) {
                try {
                    Object itemObj = m.get("item");
                    ItemStack item = null;
                    if (itemObj instanceof String s) {
                        byte[] bytes = Base64.getDecoder().decode(s);
                        item = ItemStack.deserializeBytes(bytes);
                    } else if (itemObj instanceof byte[] b) {
                        item = ItemStack.deserializeBytes(b);
                    }
                    int price = m.get("price") instanceof Number n ? n.intValue() : 0;
                    if (item != null) list.add(new ShopEntry(item, price));
                } catch (Exception ignored) {}
            }
            items.put(day, list);
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<Integer, List<ShopEntry>> e : items.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (ShopEntry se : e.getValue()) {
                Map<String, Object> m = new HashMap<>();
                m.put("item", Base64.getEncoder().encodeToString(se.item.serializeAsBytes()));
                m.put("price", se.price);
                list.add(m);
            }
            c.set("days." + e.getKey(), list);
        }
        try { c.save(file); } catch (IOException ignored) {}
    }
}