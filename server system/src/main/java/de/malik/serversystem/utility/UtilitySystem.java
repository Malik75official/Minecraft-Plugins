package de.malik.serversystem.utility;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class UtilitySystem implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ec" -> player.openInventory(player.getEnderChest());
            case "craft" -> player.openWorkbench(null, true);
            case "amboss" -> player.openAnvil(null, true);
            case "ofen" -> openNearbyFurnace(player);
            default -> sender.sendMessage("Unbekannter Utility-Befehl.");
        }
        return true;
    }

    private void openNearbyFurnace(Player player) {
        int radius = 5;
        int centerX = player.getLocation().getBlockX();
        int centerY = player.getLocation().getBlockY();
        int centerZ = player.getLocation().getBlockZ();
        Block closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = player.getWorld().getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (block.getType() != Material.FURNACE && block.getType() != Material.BLAST_FURNACE
                            && block.getType() != Material.SMOKER) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(player.getLocation());
                    if (distance < closestDistance) {
                        closest = block;
                        closestDistance = distance;
                    }
                }
            }
        }

        if (closest == null || !(closest.getState() instanceof Furnace furnace)) {
            player.sendMessage("In deiner Nähe wurde kein Ofen gefunden (max. 5 Blöcke). ");
            return;
        }
        player.openInventory(furnace.getInventory());
    }
}