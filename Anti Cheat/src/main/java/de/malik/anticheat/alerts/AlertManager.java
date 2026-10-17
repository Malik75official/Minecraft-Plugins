package de.malik.anticheat.alerts;

import de.malik.anticheat.AntiCheatPlugin;
import de.malik.anticheat.CheckType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AlertManager {
    private final AntiCheatPlugin plugin;
    private final Map<UUID, Map<CheckType, Integer>> violations = new HashMap<>();
    private final Map<UUID, Map<CheckType, Long>> lastAlertTimes = new HashMap<>();
    private final Set<UUID> mutedAlerts = new HashSet<>();
    private final Path logFile;
    private final Object logLock = new Object();

    public AlertManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        logFile = plugin.getDataFolder().toPath().resolve("logs").resolve("checks.log");
    }

    public void flag(Player player, CheckType check, String detail) {
        Map<CheckType, Integer> playerViolations = violations.computeIfAbsent(player.getUniqueId(),
                ignored -> new EnumMap<>(CheckType.class));
        int level = playerViolations.merge(check, 1, Integer::sum);

        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0, plugin.getConfig().getLong("alerts.cooldown-seconds", 10)) * 1000;
        Map<CheckType, Long> playerAlertTimes = lastAlertTimes.computeIfAbsent(player.getUniqueId(),
                ignored -> new EnumMap<>(CheckType.class));
        long lastAlert = playerAlertTimes.getOrDefault(check, 0L);
        if (now - lastAlert < cooldownMillis) {
            return;
        }
        playerAlertTimes.put(check, now);

        String message = "[AntiCheat] " + player.getName() + " " + check.getDisplayName()
                + " (VL " + level + "): " + detail;
        plugin.getLogger().warning(message);
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (recipient.hasPermission("anticheat.alerts") && !mutedAlerts.contains(recipient.getUniqueId())) {
                recipient.sendMessage(Component.text("[AntiCheat] ", NamedTextColor.RED)
                        .append(Component.text(player.getName() + " " + check.getDisplayName()
                                + " (VL " + level + "): " + detail, NamedTextColor.GRAY)));
            }
        }
        appendLog(Instant.now() + " " + message);
    }

    public boolean toggleAlerts(Player player) {
        if (mutedAlerts.remove(player.getUniqueId())) {
            return true;
        }
        mutedAlerts.add(player.getUniqueId());
        return false;
    }

    private void appendLog(String line) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (logLock) {
                try {
                    Files.writeString(logFile, line + System.lineSeparator(),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    plugin.getLogger().severe("Konnte Verdachtsmeldung nicht in checks.log schreiben: "
                            + exception.getMessage());
                }
            }
        });
    }
}
