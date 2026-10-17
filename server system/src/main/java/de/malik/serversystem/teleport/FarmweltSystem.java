package de.malik.serversystem.teleport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FarmweltSystem implements CommandExecutor {
    private static final String CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public FarmweltSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können zur Farmwelt wechseln.");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("Verwendung: /farmwelt");
            return true;
        }
        if (!config.getBoolean("farmwelt.enabled", true)) {
            player.sendMessage("Der Wechsel zur Farmwelt ist derzeit deaktiviert.");
            return true;
        }

        String targetServer = config.getString("farmwelt.target-server", "");
        if (targetServer == null || targetServer.isBlank()) {
            player.sendMessage("Der Zielserver der Farmwelt ist nicht konfiguriert.");
            plugin.getLogger().warning("farmwelt.target-server fehlt in config.yml.");
            return true;
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("Connect");
            output.writeUTF(targetServer);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
            String message = config.getString("farmwelt.connecting-message", "<green>Verbinde mit {server}...");
            player.sendMessage(miniMessage.deserialize(message,
                    TagResolver.resolver(Placeholder.unparsed("server", targetServer))));
        } catch (IOException exception) {
            plugin.getLogger().warning("Farmwelt-Wechsel konnte nicht gesendet werden: " + exception.getMessage());
            player.sendMessage("Der Wechsel zur Farmwelt konnte nicht gestartet werden.");
        }
        return true;
    }
}