package de.malik.serversystem.spawn;

import java.util.Locale;

import de.malik.serversystem.util.LocationStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnSystem implements CommandExecutor, Listener {
    private final JavaPlugin plugin;

    public SpawnSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "setspawn" -> setSpawn(sender);
            case "spawn" -> spawn(sender);
            default -> sender.sendMessage("Unbekannter Spawn-Befehl.");
        }
        return true;
    }

    private void setSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können den Spawn setzen.");
            return;
        }
        LocationStorage.write(plugin.getConfig(), "spawn.location", player.getLocation());
        plugin.saveConfig();
        sender.sendMessage("Der Server-Spawn wurde gesetzt.");
    }

    private void spawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können den Spawn verwenden.");
            return;
        }
        Location location = LocationStorage.read(plugin.getConfig(), "spawn.location");
        if (location == null) {
            sender.sendMessage("Der Server-Spawn wurde noch nicht gesetzt.");
            return;
        }
        player.teleport(location);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("spawn.override")) {
            Location joinSpawn = LocationStorage.read(plugin.getConfig(), "spawn.location");
            if (joinSpawn == null && !Bukkit.getWorlds().isEmpty()) {
                joinSpawn = Bukkit.getWorlds().get(0).getSpawnLocation();
            }
            if (joinSpawn != null) {
                player.teleport(joinSpawn);
            }
        }
        String welcome = plugin.getConfig().getString("spawn.welcome-message", "");
        if (!welcome.isBlank()) {
            player.sendMessage(welcome.replace("{player}", player.getName()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isSpawnProtected(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Der Spawn ist geschützt.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isSpawnProtected(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Der Spawn ist geschützt.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectExplosion(event.getBlock().getLocation(), event.blockList(), () -> event.setCancelled(true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectExplosion(event.getLocation(), event.blockList(), () -> event.setCancelled(true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
                && isSpawnProtected(player, player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && isSpawnProtected(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isSpawnProtected(Player player, Location location) {
        if (player.hasPermission("serversystem.protection.bypass")) {
            return false;
        }
        return isInsideSpawnProtection(location);
    }

    private void protectExplosion(Location origin, java.util.List<org.bukkit.block.Block> affectedBlocks, Runnable cancel) {
        if (isInsideSpawnProtection(origin)) {
            cancel.run();
            return;
        }
        affectedBlocks.removeIf(block -> isInsideSpawnProtection(block.getLocation()));
    }

    private boolean isInsideSpawnProtection(Location location) {
        Location spawn = LocationStorage.read(plugin.getConfig(), "spawn.location");
        if (spawn == null || !spawn.getWorld().equals(location.getWorld())) {
            return false;
        }
        int radius = Math.max(0, plugin.getConfig().getInt("spawn.protection-radius", 16));
        return spawn.distanceSquared(location) <= (double) radius * radius;
    }
}