package de.malik.anticheat.commands;

import de.malik.anticheat.AntiCheatPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AntiCheatCommand implements TabExecutor {
    private final AntiCheatPlugin plugin;

    public AntiCheatCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sendUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("alerts")) {
            toggleAlerts(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig(sender);
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        if (sender.hasPermission("anticheat.alerts")) {
            suggestions.add("alerts");
        }
        if (sender.hasPermission("anticheat.admin")) {
            suggestions.add("reload");
        }
        String prefix = args[0].toLowerCase();
        return suggestions.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private void toggleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Dieser Befehl ist nur im Spiel verfügbar.", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("anticheat.alerts")) {
            player.sendMessage(Component.text("Dafür hast du keine Berechtigung.", NamedTextColor.RED));
            return;
        }
        boolean enabled = plugin.getAlertManager().toggleAlerts(player);
        if (enabled) {
            player.sendMessage(Component.text("AntiCheat-Alerts sind aktiviert.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("AntiCheat-Alerts sind deaktiviert.", NamedTextColor.YELLOW));
        }
    }

    private void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(Component.text("Dafür hast du keine Berechtigung.", NamedTextColor.RED));
            return;
        }
        plugin.reloadAntiCheatConfig();
        sender.sendMessage(Component.text("AntiCheat-Konfiguration neu geladen.", NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Verwendung: /" + label + " <alerts|reload>", NamedTextColor.YELLOW));
    }
}
