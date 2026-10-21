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

public class QuestGui {
    public static void open(WeihnachtsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§c§l📋 Weihnachts-Quests");
        // background
        ItemStack bg = pane(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        int slot = 0;
        int[] questSlots = {10,11,12, 19,20,21, 28,29,30};
        int currentSlot = plugin.quests().currentSlot();
        List<Quest> active = currentSlot >= 0 ? plugin.quests().questsForSlot(currentSlot) : new ArrayList<>();
        for (int i = 0; i < questSlots.length; i++) {
            if (i < active.size()) {
                Quest q = active.get(i);
                int prog = plugin.quests().progress(p.getUniqueId(), q);
                boolean done = plugin.quests().isCompleted(p.getUniqueId(), q);
                boolean accepted = plugin.quests().isAccepted(p.getUniqueId(), q);
                Material mat = done ? Material.LIME_STAINED_GLASS_PANE : (accepted ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
                ItemStack it = new ItemStack(mat);
                ItemMeta m = it.getItemMeta();
                m.setDisplayName((done ? "§a" : (accepted ? "§e" : "§c")) + q.name);
                List<String> lore = new ArrayList<>();
                lore.add("§7" + q.description);
                lore.add("§7Fortschritt: §e" + prog + "/" + q.target);
                lore.add("§7Belohnung: §6" + q.rewardCookies + " Cookies" + (q.rewardItems.isEmpty() ? "" : " + Items"));
                if (!done && !accepted) lore.add("§aKlick = Annehmen");
                else if (accepted && !done) lore.add("§eIn Arbeit...");
                else lore.add("§aAbgeschlossen ✔");
                m.setLore(lore);
                it.setItemMeta(m);
                inv.setItem(questSlots[i], it);
            } else {
                ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta m = it.getItemMeta();
                m.setDisplayName("§7Keine Quest");
                it.setItemMeta(m);
                inv.setItem(questSlots[i], it);
            }
        }

        // leaderboard (top 5 by completed count) - show in slots 37-41
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort((a, b) -> Integer.compare(plugin.quests().completedCount(b.getUniqueId()), plugin.quests().completedCount(a.getUniqueId())));
        int lbSlot = 37;
        for (int i = 0; i < Math.min(5, online.size()); i++) {
            Player pl = online.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(pl);
            sm.setDisplayName("§e#" + (i + 1) + " " + pl.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Quests: §f" + plugin.quests().completedCount(pl.getUniqueId()));
            sm.setLore(lore);
            head.setItemMeta(sm);
            inv.setItem(lbSlot++, head);
        }

        // Santa info head
        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sim = (SkullMeta) info.getItemMeta();
        try {
            org.bukkit.profile.PlayerProfile profile = Bukkit.createProfile(java.util.UUID.fromString("2bd03f26-1d31-4abe-b6c6-554e0925a537"), "Santa");
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URL("http://textures.minecraft.net/texture/d161ea2369d1de96532c0b811f984c94b584dea8dc61a9acbc7e3b796c2e"));
            profile.setTextures(textures);
            sim.setOwnerProfile(profile);
        } catch (Exception ignored) {}
        sim.setDisplayName("§6§lWeihnachtsmann");
        List<String> il = new ArrayList<>();
        il.add("§7Deine Cookies: §e" + plugin.cookies().get(p.getUniqueId()));
        il.add("§7Abgeschlossene Quests: §e" + plugin.quests().completedCount(p.getUniqueId()));
        if (currentSlot >= 0) il.add("§7Aktive Slot-Zeit: §e" + (currentSlot == 0 ? "14" : (currentSlot == 1 ? "16" : "18")) + ":00 Uhr");
        else il.add("§7Keine aktiven Quests");
        sim.setLore(il);
        info.setItemMeta(sim);
        inv.setItem(49, info);

        p.openInventory(inv);
    }

    public static void handleClick(WeihnachtsPlugin plugin, org.bukkit.event.inventory.InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        int[] questSlots = {10,11,12, 19,20,21, 28,29,30};
        int idx = -1;
        for (int i = 0; i < questSlots.length; i++) {
            if (questSlots[i] == slot) { idx = i; break; }
        }
        if (idx < 0) return;
        int currentSlot = plugin.quests().currentSlot();
        if (currentSlot < 0) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Keine aktiven Quests."); return; }
        List<Quest> active = plugin.quests().questsForSlot(currentSlot);
        if (idx >= active.size()) return;
        Quest q = active.get(idx);
        if (plugin.quests().isCompleted(p.getUniqueId(), q)) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Bereits abgeschlossen."); return; }
        if (!plugin.quests().isAccepted(p.getUniqueId(), q)) {
            plugin.quests().accept(p.getUniqueId(), q);
            p.sendMessage("§c§lWeihnachtsEvent: §r§aQuest angenommen: " + q.name);
        } else {
            p.sendMessage("§c§lWeihnachtsEvent: §r§7Fortschritt: " + plugin.quests().progress(p.getUniqueId(), q) + "/" + q.target);
        }
        open(plugin, p);
    }

    private static ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }
}