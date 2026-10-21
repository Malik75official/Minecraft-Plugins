package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CookieShopAdminGui {
    private static final int[] DAY_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39
    };

    public static void open(WeihnachtsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l🍪 Cookie-Shop-Admin");
        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) { inv.setItem(i, border); inv.setItem(45 + i, border); }
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, border); inv.setItem(r * 9 + 8, border); }

        for (int i = 0; i < 24; i++) {
            int day = i + 1;
            int count = plugin.shop().forDay(day).size();
            ItemStack it = new ItemStack(count > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§eTag " + day);
            List<String> lore = new ArrayList<>();
            lore.add("§7Items: §f" + count);
            lore.add("§aKlick = bearbeiten");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(DAY_SLOTS[i], it);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§6§lHinweis");
        List<String> il = new ArrayList<>();
        il.add("§7Items hinzufügen:");
        il.add("§f/cookieshop-admin add <tag> <preis>");
        il.add("§7(Item in Hand halten)");
        il.add("§7Entfernen per Klick");
        im.setLore(il);
        inv.setItem(49, info);

        p.openInventory(inv);
    }

    public static void openDay(WeihnachtsPlugin plugin, Player p, int day) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l🍪 Admin: Tag " + day);
        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) { inv.setItem(i, border); inv.setItem(45 + i, border); }
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, border); inv.setItem(r * 9 + 8, border); }

        int slotIdx = 0;
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        for (ShopManager.ShopEntry e : plugin.shop().forDay(day)) {
            if (slotIdx >= slots.length) break;
            ItemStack it = e.item.clone();
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("§7§m----------------");
            lore.add("§6Preis: §e" + e.price);
            lore.add("§cKlick = entfernen");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(slots[slotIdx++], it);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7Zurück");
        back.setItemMeta(bm);
        inv.setItem(49, back);

        p.openInventory(inv);
        p.sendMessage("§c§lWeihnachtsEvent: §r§7Tag " + day + " – Items per /cookieshop-admin add " + day + " <preis>.");
    }

    public static void handleClick(WeihnachtsPlugin plugin, org.bukkit.event.inventory.InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();
        if (title.equals("§c§l🍪 Cookie-Shop-Admin")) {
            int slot = e.getSlot();
            // find which day slot
            for (int i = 0; i < DAY_SLOTS.length; i++) {
                if (DAY_SLOTS[i] == slot) { openDay(plugin, p, i + 1); return; }
            }
        } else if (title.startsWith("§c§l🍪 Admin: Tag ")) {
            int day = Integer.parseInt(title.replace("§c§l🍪 Admin: Tag ", "").trim());
            int slot = e.getSlot();
            if (slot == 49) { open(plugin, p); return; }
            // find entry index by slot
            int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
            int idx = -1;
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) { idx = i; break; }
            }
            if (idx < 0) return;
            java.util.List<ShopManager.ShopEntry> list = new java.util.ArrayList<>(plugin.shop().forDay(day));
            if (idx < list.size()) {
                list.remove(idx);
                plugin.shop().setDay(day, list);
                p.sendMessage("§c§lWeihnachtsEvent: §r§7Item entfernt.");
                openDay(plugin, p, day);
            }
        }
    }

    private static ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }
}