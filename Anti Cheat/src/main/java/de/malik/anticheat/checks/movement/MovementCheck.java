package de.malik.anticheat.checks.movement;

import de.malik.anticheat.AntiCheatPlugin;
import de.malik.anticheat.CheckType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MovementCheck implements Listener {
    private static final long SAMPLE_INTERVAL_TICKS = 5L;
    private final AntiCheatPlugin plugin;
    private final Map<UUID, MovementSample> samples = new HashMap<>();
    private final BukkitTask sampleTask;

    public MovementCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        sampleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::samplePlayers,
                SAMPLE_INTERVAL_TICKS, SAMPLE_INTERVAL_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        sampleTask.cancel();
    }

    private void samplePlayers() {
        if (!plugin.getConfig().getBoolean("checks.movement.enabled", true)) {
            samples.clear();
            return;
        }

        long now = System.nanoTime();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isExempt(player)) {
                samples.remove(player.getUniqueId());
                continue;
            }
            samplePlayer(player, now);
        }
    }

    private void samplePlayer(Player player, long now) {
        Location current = player.getLocation();
        MovementSample previous = samples.get(player.getUniqueId());
        if (previous == null || previous.location.getWorld() != current.getWorld()) {
            samples.put(player.getUniqueId(), new MovementSample(current.clone(), now));
            return;
        }

        double elapsedSeconds = (now - previous.timeNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        double dx = current.getX() - previous.location.getX();
        double dz = current.getZ() - previous.location.getZ();
        double horizontalSpeed = Math.hypot(dx, dz) / elapsedSeconds;
        checkSpeed(player, previous, horizontalSpeed);
        checkHover(player, previous, current, dx, dz);

        previous.location = current.clone();
        previous.timeNanos = now;
    }

    private void checkSpeed(Player player, MovementSample sample, double horizontalSpeed) {
        if (player.hasPermission(CheckType.SPEED.getBypassPermission())) {
            sample.speedViolations = 0;
            return;
        }
        double maxSpeed = plugin.getConfig().getDouble("checks.movement.max-horizontal-speed", 9.5)
                * speedPotionMultiplier(player);
        sample.speedViolations = horizontalSpeed > maxSpeed
                ? sample.speedViolations + 1 : Math.max(0, sample.speedViolations - 1);

        int requiredSamples = Math.max(1,
                plugin.getConfig().getInt("checks.movement.consecutive-speed-samples", 3));
        if (sample.speedViolations >= requiredSamples) {
            plugin.flag(player, CheckType.SPEED, String.format(java.util.Locale.ROOT,
                    "%.1f blocks/s", horizontalSpeed));
            sample.speedViolations = 0;
        }
    }

    private void checkHover(Player player, MovementSample sample, Location current, double dx, double dz) {
        if (player.hasPermission(CheckType.FLY.getBypassPermission())) {
            sample.hoverSamples = 0;
            return;
        }
        double verticalDelta = Math.abs(current.getY() - sample.location.getY());
        double horizontalDelta = Math.hypot(dx, dz);
        boolean hovering = !player.isOnGround()
                && !player.isInWater()
                && !player.isGliding()
                && !player.hasPotionEffect(PotionEffectType.LEVITATION)
                && verticalDelta < 0.03
                && horizontalDelta > 0.25;
        sample.hoverSamples = hovering ? sample.hoverSamples + 1 : 0;

        int requiredSamples = Math.max(1, plugin.getConfig().getInt("checks.movement.hover-samples", 8));
        if (sample.hoverSamples >= requiredSamples) {
            plugin.flag(player, CheckType.FLY, "anhaltende horizontale Bewegung ohne Höhenverlust");
            sample.hoverSamples = 0;
        }
    }

    private boolean isExempt(Player player) {
        return player.isInsideVehicle()
                || player.isGliding()
                || player.isInWater()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
    }

    private double speedPotionMultiplier(Player player) {
        return player.getActivePotionEffects().stream()
                .filter(effect -> effect.getType().equals(PotionEffectType.SPEED))
                .mapToDouble(effect -> 1.0 + 0.2 * (effect.getAmplifier() + 1))
                .findFirst()
                .orElse(1.0);
    }

    private static final class MovementSample {
        private Location location;
        private long timeNanos;
        private int speedViolations;
        private int hoverSamples;

        private MovementSample(Location location, long timeNanos) {
            this.location = location;
            this.timeNanos = timeNanos;
        }
    }
}
