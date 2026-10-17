package de.malik.serversystem.teleport;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import de.malik.serversystem.storage.YamlStore;
import de.malik.serversystem.util.LocationStorage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class HomeWarpSystem implements CommandExecutor {
    private static final Pattern HOME_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final String[] HOME_LOCATION_FIELDS = {"world", "x", "y", "z", "yaw", "pitch"};
    private final YamlStore playerData;
    private final YamlStore warpData;

    public HomeWarpSystem(YamlStore playerData, YamlStore warpData) {
        this.playerData = playerData;
        this.warpData = warpData;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(sender, args);
            case "home" -> home(sender, args);
            case "delhome" -> deleteHome(sender, args);
            case "homes" -> listHomes(sender, args);
            case "setwarp" -> setWarp(sender, args);
            case "warp" -> warp(sender, args);
            case "delwarp" -> deleteWarp(sender, args);
            default -> sender.sendMessage("Unbekannter Teleport-Befehl.");
        }
        return true;
    }

    private void setHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können ein Home setzen.");
            return;
        }
        if (args.length > 1) {
            sender.sendMessage("Verwendung: /sethome [Name]");
            return;
        }
        String name = normalizedHomeName(args.length == 0 ? "home" : args[0]);
        if (name == null) {
            sender.sendMessage("Home-Namen dürfen 1-32 Zeichen enthalten: Buchstaben, Zahlen, _ oder -.");
            return;
        }
        Set<String> homes = getHomeNames(player.getUniqueId());
        if (!homes.contains(name) && homes.size() >= getHomeLimit(player)) {
            sender.sendMessage("Du hast dein Home-Limit von " + getHomeLimit(player) + " erreicht.");
            return;
        }
        LocationStorage.write(playerData.data(), homePath(player.getUniqueId(), name), player.getLocation());
        playerData.save();
        sender.sendMessage("Home '" + name + "' wurde gespeichert.");
    }

    private void home(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können ein Home verwenden.");
            return;
        }
        if (args.length > 1) {
            sender.sendMessage("Verwendung: /home [Name]");
            return;
        }
        String name = normalizedHomeName(args.length == 0 ? "home" : args[0]);
        if (name == null) {
            sender.sendMessage("Ungültiger Home-Name.");
            return;
        }
        getHomeNames(player.getUniqueId());
        Location location = LocationStorage.read(playerData.data(), homePath(player.getUniqueId(), name));
        if (location == null) {
            sender.sendMessage("Das Home '" + name + "' existiert nicht.");
            return;
        }
        player.teleport(location);
        sender.sendMessage("Du wurdest zu Home '" + name + "' teleportiert.");
    }

    private void deleteHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können Homes löschen.");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /delhome <Name>");
            return;
        }
        String name = normalizedHomeName(args[0]);
        if (name == null) {
            sender.sendMessage("Ungültiger Home-Name.");
            return;
        }
        getHomeNames(player.getUniqueId());
        String path = homePath(player.getUniqueId(), name);
        if (!playerData.data().contains(path + ".world")) {
            sender.sendMessage("Das Home '" + name + "' existiert nicht.");
            return;
        }
        playerData.data().set(path, null);
        playerData.save();
        sender.sendMessage("Home '" + name + "' wurde gelöscht.");
    }

    private void listHomes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können ihre Homes auflisten.");
            return;
        }
        if (args.length != 0) {
            sender.sendMessage("Verwendung: /homes");
            return;
        }
        Set<String> homes = getHomeNames(player.getUniqueId());
        if (homes.isEmpty()) {
            sender.sendMessage("Du hast noch keine Homes gesetzt.");
            return;
        }
        sender.sendMessage("Deine Homes (" + homes.size() + "/" + getHomeLimit(player) + "): " + String.join(", ", homes));
    }

    private Set<String> getHomeNames(UUID playerId) {
        FileConfiguration data = playerData.data();
        String playerPath = "homes." + playerId;
        migrateLegacyHome(playerPath);
        ConfigurationSection homes = data.getConfigurationSection(playerPath + ".named");
        if (homes == null) {
            return Collections.emptySet();
        }
        return new TreeSet<>(homes.getKeys(false));
    }

    private void migrateLegacyHome(String playerPath) {
        FileConfiguration data = playerData.data();
        if (!data.contains(playerPath + ".world")) {
            return;
        }
        String defaultHomePath = homePath(playerPath, "home");
        if (!data.contains(defaultHomePath + ".world")) {
            for (String field : HOME_LOCATION_FIELDS) {
                if (data.contains(playerPath + "." + field)) {
                    data.set(defaultHomePath + "." + field, data.get(playerPath + "." + field));
                }
            }
        }
        for (String field : HOME_LOCATION_FIELDS) {
            data.set(playerPath + "." + field, null);
        }
        playerData.save();
    }

    private String homePath(UUID playerId, String name) {
        return homePath("homes." + playerId, name);
    }

    private String homePath(String playerPath, String name) {
        return playerPath + ".named." + name;
    }

    private String normalizedHomeName(String input) {
        String name = input.toLowerCase(Locale.ROOT);
        return HOME_NAME.matcher(name).matches() ? name : null;
    }

    private int getHomeLimit(Player player) {
        for (int limit = 25; limit >= 5; limit -= 5) {
            if (player.hasPermission("serversystem.maxhome." + limit)) {
                return limit;
            }
        }
        return 1;
    }

    private void setWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können einen Warp setzen.");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /setwarp <Name>");
            return;
        }
        LocationStorage.write(warpData.data(), "warps." + args[0].toLowerCase(Locale.ROOT), player.getLocation());
        warpData.save();
        sender.sendMessage("Warp " + args[0] + " wurde erstellt.");
    }

    private void warp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können Warps verwenden.");
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /warp <Name>");
            return;
        }
        Location location = LocationStorage.read(warpData.data(), "warps." + args[0].toLowerCase(Locale.ROOT));
        if (location == null) {
            sender.sendMessage("Dieser Warp existiert nicht.");
            return;
        }
        player.teleport(location);
    }

    private void deleteWarp(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /delwarp <Name>");
            return;
        }
        String path = "warps." + args[0].toLowerCase(Locale.ROOT);
        FileConfiguration warps = warpData.data();
        if (!warps.contains(path)) {
            sender.sendMessage("Dieser Warp existiert nicht.");
            return;
        }
        warps.set(path, null);
        warpData.save();
        sender.sendMessage("Warp entfernt.");
    }
}