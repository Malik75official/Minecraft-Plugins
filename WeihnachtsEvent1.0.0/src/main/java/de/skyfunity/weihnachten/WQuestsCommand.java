package de.skyfunity.weihnachten;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WQuestsCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    public WQuestsCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§c§lWeihnachtsEvent: §r§7Nur Spieler."); return true; }
        QuestGui.open(plugin, p);
        return true;
    }
}