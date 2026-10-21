package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CookieShopGui {
    public static void open(WeihnachtsPlugin plugin, Player p, int day) {
        Inventory inv = Bukkit.createInventory(null, 27, "Cookie-Shop Tag " + day);
        int slot = 0;
        for (ShopManager.ShopEntry e : plugin.shop().forDay(day)) {
            if (slot >= 27) break;
            ItemStack it = e.item.clone();
            org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
            m.setLore(java.util.List.of("§7Preis: " + e.price + " Cookies"));
            it.setItemMeta(m);
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    public static void handleClick(WeihnachtsPlugin plugin, org.bukkit.event.inventory.InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        if (slot < 0 || slot >= 27) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        String title = e.getView().getTitle();
        int day = 0;
        if (title.startsWith("Cookie-Shop Tag ")) {
            try { day = Integer.parseInt(title.replace("Cookie-Shop Tag ", "").trim()); } catch (Exception ignored) {}
        }
        if (day <= 0) { p.sendMessage("§cUnbekannter Shop."); return; }
        java.util.List<ShopManager.ShopEntry> list = plugin.shop().forDay(day);
        if (slot >= list.size()) { p.sendMessage("§cKein Item."); return; }
        int price = list.get(slot).price;
        if (price <= 0) { p.sendMessage("§cKein Preis."); return; }
        if (!plugin.cookies().take(p.getUniqueId(), price)) { p.sendMessage("§cNicht genug Cookies (hast " + plugin.cookies().get(p.getUniqueId()) + ", brauchst " + price + ")."); return; }
        p.getInventory().addItem(list.get(slot).item.clone());
        p.sendMessage("§aGekauft!");
    }
}