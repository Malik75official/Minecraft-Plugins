package de.skyfunity.weihnachten;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public class GiftCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    public GiftCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§c§lWeihnachtsEvent: §r§7Nur Spieler."); return true; }
        if (args.length < 2) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Verwendung: /gift <spieler> <cookies>"); return true; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Spieler nicht online."); return true; }
        int amount;
        try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException e) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Ungültige Zahl."); return true; }
        if (amount <= 0) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Betrag muss positiv sein."); return true; }
        if (!plugin.cookies().take(p.getUniqueId(), amount)) { p.sendMessage("§c§lWeihnachtsEvent: §r§7Nicht genug Cookies."); return true; }
        plugin.cookies().add(target.getUniqueId(), amount);
        p.sendMessage("§c§lWeihnachtsEvent: §r§7§aDu hast " + target.getName() + " " + amount + " Cookies geschenkt.");
        target.sendMessage("§c§lWeihnachtsEvent: §r§7§aDu hast " + amount + " Cookies von " + p.getName() + " erhalten.");
        return true;
    }
}