package de.malik.serversystem.links;

import java.util.Locale;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class LinkCommandSystem implements CommandExecutor {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LinkCommandSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0) {
            sender.sendMessage("Verwendung: /" + command.getName());
            return true;
        }

        String path = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "discord" -> "links.discord";
            case "webseite" -> "links.webseite";
            case "webshop" -> "links.webshop";
            default -> null;
        };
        if (path == null) {
            sender.sendMessage("Unbekannter Link-Befehl.");
            return true;
        }

        String message = plugin.getConfig().getString(path, "");
        if (message.isBlank()) {
            sender.sendMessage("Diese Nachricht ist in config.yml noch nicht eingerichtet.");
            return true;
        }
        sender.sendMessage(miniMessage.deserialize(message));
        return true;
    }
}