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

public class AdventCalendarGui {
    private static final int[] DAY_SLOTS = {
            10,11,12,13,14,15,
            19,20,21,22,23,24,
            28,29,30,31,32,33,
            37,38,39,40,41,42
    };

    public static void open(WeihnachtsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l🎄 Adventskalender 🎄");

        // fill background
        ItemStack bg = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta bgm = bg.getItemMeta(); bgm.setDisplayName(" "); bg.setItemMeta(bgm);
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // border with snow
        ItemStack snow = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta snowMeta = snow.getItemMeta(); snowMeta.setDisplayName("§b❄"); snow.setItemMeta(snowMeta);
        for (int i = 0; i < 9; i++) { inv.setItem(i, snow); inv.setItem(45 + i, snow); }
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, snow); inv.setItem(r * 9 + 8, snow); }

        int today = java.time.LocalDate.now().getDayOfMonth();
        for (int i = 0; i < 24; i++) {
            int day = i + 1;
            boolean claimed = plugin.advent().hasClaimed(p.getUniqueId(), day);
            boolean available = day <= today;
            Material mat;
            String name;
            if (claimed) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                name = "§a§lTag " + day + " §7✔";
            } else if (available) {
                mat = Material.RED_STAINED_GLASS_PANE;
                name = "§c§lTag " + day + " §e(klick)";
            } else {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                name = "§7Tag " + day + " §8(locked)";
            }
            ItemStack it = new ItemStack(mat);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add(claimed ? "§aBereits abgeholt" : (available ? "§eKlicke zum Abholen" : "§8Noch nicht verfügbar"));
            if (day == today && !claimed) lore.add("§6Heute!");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(DAY_SLOTS[i], it);
        }

        // info item in center bottom (Weihnachtsmann-Kopf)
        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) info.getItemMeta();
        try {
            org.bukkit.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(java.util.UUID.fromString("2bd03f26-1d31-4abe-b6c6-554e0925a537"), "Santa");
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URL("http://textures.minecraft.net/texture/d161ea2369d1de96532c0b811f984c94b584dea8dc61a9acbc7e3b796c2e"));
            profile.setTextures(textures);
            sm.setOwnerProfile(profile);
        } catch (Exception ignored) {}
        sm.setDisplayName("§6§lWeihnachtsmann");
        List<String> il = new ArrayList<>();
        il.add("§7Abgeholt: §e" + plugin.advent().claimed(p.getUniqueId()).size() + "/24");
        if (plugin.advent().allClaimed(p.getUniqueId())) {
            il.add(plugin.advent().bonusClaimed(p.getUniqueId()) ? "§aBonus bereits erhalten!" : "§6Alle 24 Tage → Bonus!");
        } else {
            il.add("§7Cookies: §e" + plugin.cookies().get(p.getUniqueId()));
        }
        sm.setLore(il);
        info.setItemMeta(sm);
        inv.setItem(49, info);

        p.openInventory(inv);
    }

    public static void handleClick(WeihnachtsPlugin plugin, org.bukkit.event.inventory.InventoryClickEvent e) {
        int slot = e.getSlot();
        int day = -1;
        for (int i = 0; i < DAY_SLOTS.length; i++) {
            if (DAY_SLOTS[i] == slot) { day = i + 1; break; }
        }
        if (day == -1) return;
        Player p = (Player) e.getWhoClicked();
        int today = java.time.LocalDate.now().getDayOfMonth();
        if (day > today) { p.sendMessage("§c§lWeihnachtsEvent: §r§7§cDieser Tag ist noch nicht verfügbar."); return; }
        if (plugin.advent().hasClaimed(p.getUniqueId(), day)) { p.sendMessage("§c§lWeihnachtsEvent: §r§7§eBereits abgeholt."); return; }
        plugin.advent().claim(p.getUniqueId(), day);
        plugin.cookies().add(p.getUniqueId(), 5);
        p.sendMessage("§c§lWeihnachtsEvent: §r§7§aTag " + day + " abgeholt! +5 Cookies");
        if (plugin.advent().allClaimed(p.getUniqueId()) && !plugin.advent().bonusClaimed(p.getUniqueId())) {
            plugin.advent().setBonus(p.getUniqueId());
            plugin.cookies().add(p.getUniqueId(), 50);
            p.sendMessage("§c§lWeihnachtsEvent: §r§7§6§lBonus! §aAlle 24 Tage abgeholt: +50 Cookies");
        }
        open(plugin, p);
    }
}