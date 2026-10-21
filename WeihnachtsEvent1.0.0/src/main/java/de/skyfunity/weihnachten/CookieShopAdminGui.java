package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

public class CookieShopAdminGui {
    public static void open(WeihnachtsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "Cookie-Shop-Admin: Tage");
        for (int i = 1; i <= 24; i++) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("Tag " + i + " (" + plugin.shop().forDay(i).size() + " Items)");
            it.setItemMeta(m);
            inv.setItem(i - 1, it);
        }
        p.openInventory(inv);
    }

    public static void openDay(WeihnachtsPlugin plugin, Player p, int day) {
        Inventory inv = Bukkit.createInventory(null, 27, "Cookie-Shop-Admin: Tag " + day);
        int slot = 0;
        for (ShopManager.ShopEntry e : plugin.shop().forDay(day)) {
            if (slot >= 27) break;
            ItemStack it = e.item.clone();
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.hasLore() ? m.getLore() : new java.util.ArrayList<>();
            lore.add("§7Preis: " + e.price);
            lore.add("§cKlick = entfernen");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
        p.sendMessage("§eTag " + day + ": Items per /cookieshop-admin add " + day + " <preis> (Item in Hand). Entfernen per Klick.");
    }

    public static void handleClick(WeihnachtsPlugin plugin, org.bukkit.event.inventory.InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();
        if (title.equals("Cookie-Shop-Admin: Tage")) {
            int slot = e.getSlot();
            if (slot < 0 || slot > 23) return;
            openDay(plugin, p, slot + 1);
        } else if (title.startsWith("Cookie-Shop-Admin: Tag ")) {
            int day = Integer.parseInt(title.replace("Cookie-Shop-Admin: Tag ", "").trim());
            int slot = e.getSlot();
            if (slot < 0 || slot >= 27) return;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;
            // remove entry at slot index
            java.util.List<ShopManager.ShopEntry> list = new java.util.ArrayList<>(plugin.shop().forDay(day));
            if (slot < list.size()) {
                list.remove(slot);
                plugin.shop().setDay(day, list);
                p.sendMessage("§aItem entfernt.");
                openDay(plugin, p, day);
            }
        }
    }
}