package de.skyfunity.weihnachten;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CookieShopCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    public CookieShopCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
        CookieShopGui.open(plugin, p, java.time.LocalDate.now().getDayOfMonth());
        return true;
    }
}