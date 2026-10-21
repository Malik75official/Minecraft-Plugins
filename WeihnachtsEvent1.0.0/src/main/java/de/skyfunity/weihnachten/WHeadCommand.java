package de.skyfunity.weihnachten;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class WHeadCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    public WHeadCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
        if (!p.hasPermission("Weihnachten.admin")) { p.sendMessage("Keine Berechtigung."); return true; }
        if (args.length != 1) { p.sendMessage("Verwendung: /whead <cookies>"); return true; }
        int amount;
        try { amount = Integer.parseInt(args[0]); } catch (NumberFormatException e) { p.sendMessage("Ungültige Zahl."); return true; }
        Block target = p.getTargetBlockExact(5);
        if (target == null) { p.sendMessage("Schau einen Block an."); return true; }
        plugin.heads().register(target.getLocation(), amount);
        p.sendMessage("§aKopf registriert (" + amount + " Cookies).");
        return true;
    }
}