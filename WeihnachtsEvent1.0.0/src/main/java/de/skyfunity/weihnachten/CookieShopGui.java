package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class CookieShopGui {
    private static final int[] ITEM_SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};

    public static void open(WeihnachtsPlugin plugin, Player p, int day) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l🍪 Cookie-Shop §7(Tag " + day + ")");
        // border
        ItemStack border = pane(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) { inv.setItem(i, border); inv.setItem(45 + i, border); }
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, border); inv.setItem(r * 9 + 8, border); }

        int slotIdx = 0;
        for (ShopManager.ShopEntry e : plugin.shop().forDay(day)) {
            if (slotIdx >= ITEM_SLOTS.length) break;
            ItemStack it = e.item.clone();
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("§7§m----------------");
            lore.add("§6Preis: §e" + e.price + " §6Cookies");
            lore.add("§aKlick = Kaufen");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(ITEM_SLOTS[slotIdx++], it);
        }

        // info item (Weihnachtsmann-Kopf)
        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta im = (SkullMeta) info.getItemMeta();
        try {
            org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(java.util.UUID.fromString("2bd03f26-1d31-4abe-b6c6-554e0925a537"), "Santa");
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URL("http://textures.minecraft.net/texture/d161ea2369d1de96532c0b811f984c94b584dea8dc61a9acbc7e3b796c2e"));
            profile.setTextures(textures);
            im.setOwnerProfile(profile);
        } catch (Exception ignored) {}
        im.setDisplayName("§6§lDeine Cookies");
        List<String> il = new ArrayList<>();
        il.add("§7Balance: §e" + plugin.cookies().get(p.getUniqueId()));
        il.add("§7Tag: §e" + day);
        im.setLore(il);
        info.setItemMeta(im);
        inv.setItem(49, info);

        p.openInventory(inv);
    }

    public static void handleClick(WeihnachtsPlugin plugin, org.bukkit.event.inventory.InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        String title = e.getView().getTitle();
        int day = 0;
        if (title.contains("Tag ")) {
            try { day = Integer.parseInt(title.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
        }
        if (day <= 0) { p.sendMessage(msg("Unbekannter Shop.")); return; }
        java.util.List<ShopManager.ShopEntry> list = plugin.shop().forDay(day);
        int idx = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) { idx = i; break; }
        }
        if (idx < 0 || idx >= list.size()) { return; }
        int price = list.get(idx).price;
        if (price <= 0) { p.sendMessage(msg("Kein Preis.")); return; }
        if (!plugin.cookies().take(p.getUniqueId(), price)) {
            p.sendMessage(msg("Nicht genug Cookies (hast " + plugin.cookies().get(p.getUniqueId()) + ", brauchst " + price + ")."));
            return;
        }
        p.getInventory().addItem(list.get(idx).item.clone());
        p.sendMessage(msg("Gekauft! (-" + price + " Cookies)"));
    }

    private static ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    private static String msg(String s) { return "§c§lWeihnachtsEvent: §r§7" + s; }
}