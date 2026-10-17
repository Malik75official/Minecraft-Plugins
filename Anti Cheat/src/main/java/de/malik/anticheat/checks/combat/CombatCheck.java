package de.malik.anticheat.checks.combat;

import de.malik.anticheat.AntiCheatPlugin;
import de.malik.anticheat.CheckType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatCheck implements Listener {
    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> swingTimes = new HashMap<>();

    public CombatCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("checks.combat.enabled", true)
                || !(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)
                || player.hasPermission(CheckType.REACH.getBypassPermission())
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        double reach = distanceToBox(player.getEyeLocation(), target.getBoundingBox());
        double maxReach = plugin.getConfig().getDouble("checks.combat.max-reach", 3.6);
        if (reach > maxReach) {
            plugin.flag(player, CheckType.REACH, String.format(java.util.Locale.ROOT,
                    "%.2f blocks (limit %.2f)", reach, maxReach));
        }
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (!plugin.getConfig().getBoolean("checks.combat.enabled", true)
                || event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (event.getPlayer().hasPermission(CheckType.AUTOCLICKER.getBypassPermission())) {
            swingTimes.remove(event.getPlayer().getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> times = swingTimes.computeIfAbsent(event.getPlayer().getUniqueId(),
                ignored -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > 1000) {
            times.removeFirst();
        }

        int maxCps = Math.max(1, plugin.getConfig().getInt("checks.combat.max-clicks-per-second", 20));
        if (times.size() > maxCps) {
            plugin.flag(event.getPlayer(), CheckType.AUTOCLICKER, times.size() + " Arm-Swings in 1 Sekunde");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        swingTimes.remove(event.getPlayer().getUniqueId());
    }

    private double distanceToBox(Location eye, BoundingBox box) {
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        return eye.toVector().distance(new org.bukkit.util.Vector(x, y, z));
    }
}
