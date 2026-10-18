package de.malik.serversystem.mob;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class MobSpawnCommand implements CommandExecutor, TabCompleter {
    private final FileConfiguration config;
    private final MobDisplayManager mobDisplayManager;

    public MobSpawnCommand(FileConfiguration config, MobDisplayManager mobDisplayManager) {
        this.config = config;
        this.mobDisplayManager = mobDisplayManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mobsystem.spawn")) {
            sender.sendMessage("§cDafuer hast du keine Berechtigung.");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            return false;
        }

        EntityType entityType = parseMobType(args[0]);
        if (entityType == null) {
            sender.sendMessage("§cUnbekannter Mob. Beispiel: §e/mobspawn cow 5");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cDas Level muss eine ganze Zahl sein.");
            return true;
        }

        int maxLevel = Math.max(1, config.getInt("levels.command-max-level", 100));
        if (level < 1 || level > maxLevel) {
            sender.sendMessage("§cDas Level muss zwischen 1 und " + maxLevel + " liegen.");
            return true;
        }

        Player target;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cDieser Spieler ist nicht online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cGib als Konsole einen Spieler an: /mobspawn <mob> <level> <spieler>");
            return true;
        }

        Location spawnLocation = target.getLocation().clone();
        spawnLocation.setPitch(0.0F);
        spawnLocation.add(spawnLocation.getDirection().multiply(2.0));

        Entity spawned;
        try {
            spawned = target.getWorld().spawnEntity(spawnLocation, entityType);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§cDieser Mob kann an dieser Stelle nicht gespawnt werden.");
            return true;
        }

        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            sender.sendMessage("§cDieser Entity-Typ ist kein Mob oder Tier.");
            return true;
        }

        mobDisplayManager.setLevel(mob, level);
        sender.sendMessage("§a" + entityType.name().toLowerCase(Locale.ROOT)
                + " auf Level " + level + " bei " + target.getName() + " gespawnt.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(EntityType.values())
                    .filter(type -> Mob.class.isAssignableFrom(type.getEntityClass()))
                    .map(type -> type.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String prefix = args[1];
            return List.of("1", "2", "3", "5", "10")
                    .stream()
                    .filter(level -> level.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private EntityType parseMobType(String input) {
        String name = input.toLowerCase(Locale.ROOT);
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        name = name.replace('-', '_');

        try {
            EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            return Mob.class.isAssignableFrom(type.getEntityClass()) ? type : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
