package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager implements Listener {
    private final WeihnachtsPlugin plugin;
    private final File file;
    // playerId -> questId -> progress
    private final Map<java.util.UUID, Map<String, Integer>> progress = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Set<String>> completed = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Set<String>> accepted = new ConcurrentHashMap<>();

    private final List<Quest> quests = new ArrayList<>();
    private final int[] slotHours = {14, 16, 18};

    public QuestManager(WeihnachtsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        loadDefaults();
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startScheduler();
    }

    private void loadDefaults() {
        // 9 quests, 3 per slot (14,16,18 Uhr), each 2h
        // Slot 0 (14 Uhr)
        quests.add(new Quest("walk_5000", "Langer Weg", "Laufe 5000 Bloecke", Quest.Type.WALK, 5000, 0, List.of(), 20));
        quests.add(new Quest("jump_500", "Huepfer", "Springe 500 Mal", Quest.Type.JUMP, 500, 0, List.of(), 15));
        quests.add(new Quest("snowball_50", "Schneeballschlacht", "Wirf 50 Schneebaelle", Quest.Type.SNOWBALL, 50, 0, List.of(), 10));
        // Slot 1 (16 Uhr)
        quests.add(new Quest("mine_diamond_20", "Diamantenjagd", "Baue 20 Diamanten ab", Quest.Type.MINE_DIAMOND, 20, 1, List.of(), 30));
        quests.add(new Quest("kill_pig_10", "Schweinejagd", "Toete 10 Schweine", Quest.Type.KILL_PIG, 10, 1, List.of(), 15));
        quests.add(new Quest("place_100", "Bauherr", "Platziere 100 Bloecke", Quest.Type.PLACE_BLOCK, 100, 1, List.of(), 20));
        // Slot 2 (18 Uhr)
        quests.add(new Quest("fish_15", "Winterangeln", "Fange 15 Fische", Quest.Type.FISH, 15, 2, List.of(), 20));
        quests.add(new Quest("eat_20", "Weihnachtsessen", "Iss 20 Nahrungsmittel", Quest.Type.EAT, 20, 2, List.of(), 10));
        quests.add(new Quest("kill_mob_25", "Monsterjaeger", "Toete 25 Monster", Quest.Type.KILL_MOB, 25, 2, List.of(), 25));
        // Extra quests (more variety)
        quests.add(new Quest("break_200", "Abbruch", "Baue 200 Bloecke ab", Quest.Type.BREAK_BLOCK, 200, 0, List.of(), 15));
        quests.add(new Quest("craft_10", "Handwerker", "Stelle 10 Items her", Quest.Type.CRAFT, 10, 1, List.of(), 15));
        quests.add(new Quest("shear_10", "Scherer", "Scheere 10 Schafe", Quest.Type.SHEAR, 10, 2, List.of(), 15));
        quests.add(new Quest("enter_nether", "Netherreise", "Betritt den Nether", Quest.Type.ENTER_NETHER, 1, 0, List.of(), 10));
        quests.add(new Quest("enter_end", "Endreise", "Betritt das End", Quest.Type.ENTER_END, 1, 1, List.of(), 10));
        quests.add(new Quest("kill_player_3", "PvP", "Besiege 3 Spieler", Quest.Type.KILL_PLAYER, 3, 2, List.of(), 30));
        // --- 40 weitere Quests ---
        quests.add(new Quest("walk_2000", "Spaziergang", "Laufe 2000 Bloecke", Quest.Type.WALK, 2000, 0, List.of(), 8));
        quests.add(new Quest("walk_7500", "Wanderer", "Laufe 7500 Bloecke", Quest.Type.WALK, 7500, 1, List.of(), 25));
        quests.add(new Quest("walk_15000", "Ultramarathon", "Laufe 15000 Bloecke", Quest.Type.WALK, 15000, 2, List.of(), 50));
        quests.add(new Quest("jump_200", "Huepferlein", "Springe 200 Mal", Quest.Type.JUMP, 200, 0, List.of(), 8));
        quests.add(new Quest("jump_750", "Springer", "Springe 750 Mal", Quest.Type.JUMP, 750, 1, List.of(), 20));
        quests.add(new Quest("jump_2000", "Frosch", "Springe 2000 Mal", Quest.Type.JUMP, 2000, 2, List.of(), 40));
        quests.add(new Quest("snowball_20", "Schneeflocke", "Wirf 20 Schneebaelle", Quest.Type.SNOWBALL, 20, 0, List.of(), 5));
        quests.add(new Quest("snowball_100", "Schneesturm", "Wirf 100 Schneebaelle", Quest.Type.SNOWBALL, 100, 1, List.of(), 15));
        quests.add(new Quest("snowball_200", "Lawine", "Wirf 200 Schneebaelle", Quest.Type.SNOWBALL, 200, 2, List.of(), 25));
        quests.add(new Quest("mine_diamond_5", "Gluecksgriff", "Baue 5 Diamanten ab", Quest.Type.MINE_DIAMOND, 5, 0, List.of(), 12));
        quests.add(new Quest("mine_diamond_50", "Diamantenkoenig", "Baue 50 Diamanten ab", Quest.Type.MINE_DIAMOND, 50, 2, List.of(), 60));
        quests.add(new Quest("kill_pig_5", "Schweinchen", "Toete 5 Schweine", Quest.Type.KILL_PIG, 5, 0, List.of(), 8));
        quests.add(new Quest("kill_pig_25", "Schlachter", "Toete 25 Schweine", Quest.Type.KILL_PIG, 25, 1, List.of(), 25));
        quests.add(new Quest("kill_pig_50", "Metzger", "Toete 50 Schweine", Quest.Type.KILL_PIG, 50, 2, List.of(), 40));
        quests.add(new Quest("kill_mob_10", "Jaeger", "Toete 10 Monster", Quest.Type.KILL_MOB, 10, 0, List.of(), 12));
        quests.add(new Quest("kill_mob_50", "Monsterjaeger", "Toete 50 Monster", Quest.Type.KILL_MOB, 50, 1, List.of(), 35));
        quests.add(new Quest("kill_mob_100", "Monstervernichter", "Toete 100 Monster", Quest.Type.KILL_MOB, 100, 2, List.of(), 60));
        quests.add(new Quest("eat_10", "Snack", "Iss 10 Nahrungsmittel", Quest.Type.EAT, 10, 0, List.of(), 5));
        quests.add(new Quest("eat_40", "Schlemmer", "Iss 40 Nahrungsmittel", Quest.Type.EAT, 40, 1, List.of(), 20));
        quests.add(new Quest("eat_80", "Vielfrass", "Iss 80 Nahrungsmittel", Quest.Type.EAT, 80, 2, List.of(), 35));
        quests.add(new Quest("fish_5", "Angler", "Fange 5 Fische", Quest.Type.FISH, 5, 0, List.of(), 10));
        quests.add(new Quest("fish_30", "Profiangler", "Fange 30 Fische", Quest.Type.FISH, 30, 1, List.of(), 30));
        quests.add(new Quest("fish_60", "Meisterangler", "Fange 60 Fische", Quest.Type.FISH, 60, 2, List.of(), 50));
        quests.add(new Quest("place_50", "Bauanfaenger", "Platziere 50 Bloecke", Quest.Type.PLACE_BLOCK, 50, 0, List.of(), 10));
        quests.add(new Quest("place_200", "Baumeister", "Platziere 200 Bloecke", Quest.Type.PLACE_BLOCK, 200, 1, List.of(), 30));
        quests.add(new Quest("place_500", "Architekt", "Platziere 500 Bloecke", Quest.Type.PLACE_BLOCK, 500, 2, List.of(), 60));
        quests.add(new Quest("break_100", "Abbrecher", "Baue 100 Bloecke ab", Quest.Type.BREAK_BLOCK, 100, 0, List.of(), 10));
        quests.add(new Quest("break_400", "Abrissbirne", "Baue 400 Bloecke ab", Quest.Type.BREAK_BLOCK, 400, 1, List.of(), 30));
        quests.add(new Quest("break_800", "Zerstoerer", "Baue 800 Bloecke ab", Quest.Type.BREAK_BLOCK, 800, 2, List.of(), 50));
        quests.add(new Quest("craft_5", "Bastler", "Stelle 5 Items her", Quest.Type.CRAFT, 5, 0, List.of(), 8));
        quests.add(new Quest("craft_25", "Handwerker", "Stelle 25 Items her", Quest.Type.CRAFT, 25, 1, List.of(), 20));
        quests.add(new Quest("craft_60", "Meisterhandwerker", "Stelle 60 Items her", Quest.Type.CRAFT, 60, 2, List.of(), 40));
        quests.add(new Quest("shear_5", "Scherer", "Scheere 5 Schafe", Quest.Type.SHEAR, 5, 0, List.of(), 8));
        quests.add(new Quest("shear_20", "Wollsammler", "Scheere 20 Schafe", Quest.Type.SHEAR, 20, 1, List.of(), 20));
        quests.add(new Quest("shear_40", "Schafschur", "Scheere 40 Schafe", Quest.Type.SHEAR, 40, 2, List.of(), 35));
        quests.add(new Quest("enter_nether_2", "Netherpendler", "Betritt den Nether 2 Mal", Quest.Type.ENTER_NETHER, 2, 1, List.of(), 12));
        quests.add(new Quest("enter_end_2", "Endpendler", "Betritt das End 2 Mal", Quest.Type.ENTER_END, 2, 2, List.of(), 12));
        quests.add(new Quest("kill_player_1", "Duellant", "Besiege 1 Spieler", Quest.Type.KILL_PLAYER, 1, 0, List.of(), 15));
        quests.add(new Quest("kill_player_5", "Killer", "Besiege 5 Spieler", Quest.Type.KILL_PLAYER, 5, 1, List.of(), 40));
        quests.add(new Quest("kill_player_10", "Champion", "Besiege 10 Spieler", Quest.Type.KILL_PLAYER, 10, 2, List.of(), 70));
    }

    public List<Quest> allQuests() { return quests; }
    public List<Quest> questsForSlot(int slot) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : quests) if (q.slot == slot) out.add(q);
        return out;
    }

    public int currentSlot() {
        int h = java.time.LocalTime.now().getHour();
        if (h >= 14 && h < 16) return 0;
        if (h >= 16 && h < 18) return 1;
        if (h >= 18 && h < 20) return 2;
        return -1;
    }

    public boolean isActive(Quest q) {
        int slot = currentSlot();
        return slot == q.slot;
    }

    public int progress(java.util.UUID u, Quest q) {
        return progress.computeIfAbsent(u, x -> new HashMap<>()).getOrDefault(q.id, 0);
    }

    public boolean isCompleted(java.util.UUID u, Quest q) {
        return completed.computeIfAbsent(u, x -> new HashSet<>()).contains(q.id);
    }

    public boolean isAccepted(java.util.UUID u, Quest q) {
        return accepted.computeIfAbsent(u, x -> new HashSet<>()).contains(q.id);
    }

    public void accept(java.util.UUID u, Quest q) {
        accepted.computeIfAbsent(u, x -> new HashSet<>()).add(q.id);
        progress.computeIfAbsent(u, x -> new HashMap<>()).putIfAbsent(q.id, 0);
        save();
    }

    public void addProgress(java.util.UUID u, Quest.Type type, int amount) {
        for (Quest q : quests) {
            if (q.type != type) continue;
            if (!isActive(q)) continue;
            if (!isAccepted(u, q)) continue;
            if (isCompleted(u, q)) continue;
            Map<String, Integer> p = progress.computeIfAbsent(u, x -> new HashMap<>());
            int cur = p.getOrDefault(q.id, 0) + amount;
            if (cur > q.target) cur = q.target;
            p.put(q.id, cur);
            if (cur >= q.target) {
                complete(u, q);
            }
            save();
        }
    }

    private void complete(java.util.UUID u, Quest q) {
        completed.computeIfAbsent(u, x -> new HashSet<>()).add(q.id);
        plugin.cookies().add(u, q.rewardCookies);
        // give item rewards
        Player pl = Bukkit.getPlayer(u);
        if (pl != null) {
            for (org.bukkit.inventory.ItemStack it : q.rewardItems) {
                pl.getInventory().addItem(it.clone());
            }
            pl.sendMessage("§c§lWeihnachtsEvent: §r§aQuest abgeschlossen: " + q.name + " (+ " + q.rewardCookies + " Cookies)");
        }
        save();
    }

    public int completedCount(java.util.UUID u) {
        return completed.getOrDefault(u, new HashSet<>()).size();
    }

    // Event handlers
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        int slot = currentSlot();
        if (slot >= 0) {
            for (Quest q : questsForSlot(slot)) {
                accept(e.getPlayer().getUniqueId(), q);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        double dist = e.getFrom().distance(e.getTo());
        if (dist > 0 && dist < 5) {
            addProgress(e.getPlayer().getUniqueId(), Quest.Type.WALK, (int) Math.round(dist));
        }
    }

    @EventHandler
    public void onJump(PlayerToggleSneakEvent e) {
        // placeholder: jump detection via move Y delta is complex; use sneak as proxy? We'll track via move
    }

    private int lastY = 0;
    @EventHandler
    public void onMoveJump(PlayerMoveEvent e) {
        if (e.getTo().getY() > e.getFrom().getY() + 0.4) {
            addProgress(e.getPlayer().getUniqueId(), Quest.Type.JUMP, 1);
        }
    }

    @EventHandler
    public void onSnowball(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Snowball) {
            org.bukkit.projectiles.ProjectileSource shooter = ((org.bukkit.entity.Projectile) e.getEntity()).getShooter();
            if (shooter instanceof Player) {
                addProgress(((Player) shooter).getUniqueId(), Quest.Type.SNOWBALL, 1);
            }
        }
    }

    @EventHandler
    public void onMine(BlockBreakEvent e) {
        if (e.getBlock().getType() == org.bukkit.Material.DIAMOND_ORE || e.getBlock().getType() == org.bukkit.Material.DEEPSLATE_DIAMOND_ORE) {
            addProgress(e.getPlayer().getUniqueId(), Quest.Type.MINE_DIAMOND, 1);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        java.util.UUID killer = e.getEntity().getKiller().getUniqueId();
        if (e.getEntity() instanceof org.bukkit.entity.Pig) {
            addProgress(killer, Quest.Type.KILL_PIG, 1);
        }
        if (!(e.getEntity() instanceof org.bukkit.entity.Player)) {
            addProgress(killer, Quest.Type.KILL_MOB, 1);
        } else {
            addProgress(killer, Quest.Type.KILL_PLAYER, 1);
        }
    }

    @EventHandler
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        addProgress(e.getPlayer().getUniqueId(), Quest.Type.PLACE_BLOCK, 1);
    }

    @EventHandler
    public void onBreakExtra(org.bukkit.event.block.BlockBreakEvent e) {
        addProgress(e.getPlayer().getUniqueId(), Quest.Type.BREAK_BLOCK, 1);
    }

    @EventHandler
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            addProgress(p.getUniqueId(), Quest.Type.CRAFT, 1);
        }
    }

    @EventHandler
    public void onShear(org.bukkit.event.player.PlayerShearEntityEvent e) {
        addProgress(e.getPlayer().getUniqueId(), Quest.Type.SHEAR, 1);
    }

    @EventHandler
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        String name = e.getPlayer().getWorld().getEnvironment().name();
        if (name.equals("NETHER")) addProgress(e.getPlayer().getUniqueId(), Quest.Type.ENTER_NETHER, 1);
        if (name.equals("THE_END")) addProgress(e.getPlayer().getUniqueId(), Quest.Type.ENTER_END, 1);
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e) {
        addProgress(e.getPlayer().getUniqueId(), Quest.Type.EAT, 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            addProgress(e.getPlayer().getUniqueId(), Quest.Type.FISH, 1);
        }
    }

    private void startScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int h = java.time.LocalTime.now().getHour();
            int m = java.time.LocalTime.now().getMinute();
            if (m == 0 && (h == 14 || h == 16 || h == 18)) {
                int slot = h == 14 ? 0 : (h == 16 ? 1 : 2);
                announceSlot(slot);
            }
            // announce 10 min before
            if (m == 50 && (h == 13 || h == 15 || h == 17)) {
                int slot = h == 13 ? 0 : (h == 15 ? 1 : 2);
                int hour = slotHours[slot];
                Bukkit.broadcastMessage("§c§lWeihnachtsEvent: §r§eIn 10 Minuten starten die " + hour + ":00 Uhr Quests!");
            }
        }, 20L * 30, 20L * 30);
    }

    private void announceSlot(int slot) {
        int hour = slotHours[slot];
        Bukkit.broadcastMessage("§c§lWeihnachtsEvent: §r§aDie " + hour + ":00 Uhr Quests sind jetzt verfügbar! /wquests");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("§c§lWeihnachtsEvent: §r§7Du hast 2 Stunden Zeit, die Quests zu erfüllen.");
            for (Quest q : questsForSlot(slot)) {
                accept(p.getUniqueId(), q);
            }
        }
    }

    private void load() {
        // persistence of progress/completed/accepted
        File f = file;
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration c = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        if (c.getConfigurationSection("players") == null) return;
        for (String k : c.getConfigurationSection("players").getKeys(false)) {
            java.util.UUID u = java.util.UUID.fromString(k);
            progress.put(u, new HashMap<>());
            completed.put(u, new HashSet<>());
            accepted.put(u, new HashSet<>());
            if (c.getConfigurationSection("players." + k + ".progress") != null) {
                for (String q : c.getConfigurationSection("players." + k + ".progress").getKeys(false)) {
                    progress.get(u).put(q, c.getInt("players." + k + ".progress." + q));
                }
            }
            completed.get(u).addAll(c.getStringList("players." + k + ".completed"));
            accepted.get(u).addAll(c.getStringList("players." + k + ".accepted"));
        }
    }

    public void save() {
        org.bukkit.configuration.file.YamlConfiguration c = new org.bukkit.configuration.file.YamlConfiguration();
        for (java.util.UUID u : progress.keySet()) {
            c.set("players." + u + ".progress", progress.get(u));
            c.set("players." + u + ".completed", new ArrayList<>(completed.getOrDefault(u, new HashSet<>())));
            c.set("players." + u + ".accepted", new ArrayList<>(accepted.getOrDefault(u, new HashSet<>())));
        }
        try { c.save(file); } catch (java.io.IOException ignored) {}
    }
}