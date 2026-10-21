package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.UUID;

public class CookieAdminCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    public CookieAdminCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Weihnachten.admin")) { sender.sendMessage(msg("Keine Berechtigung.")); return true; }
        if (args.length < 3) { sender.sendMessage(msg("Verwendung: /cookie-admin <add|remove|set> <cookies> <spieler>")); return true; }
        String action = args[0].toLowerCase();
        int amount;
        try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sender.sendMessage(msg("Ungültige Zahl.")); return true; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(msg("Spieler nicht gefunden/online.")); return true; }
        UUID u = target.getUniqueId();
        switch (action) {
            case "add" -> { plugin.cookies().add(u, amount); sender.sendMessage(msg("+" + amount + " Cookies für " + target.getName() + ". Neu: " + plugin.cookies().get(u))); }
            case "remove" -> {
                if (!plugin.cookies().take(u, amount)) { sender.sendMessage(msg("Nicht genug Cookies (hat " + plugin.cookies().get(u) + ").")); return true; }
                sender.sendMessage(msg("-" + amount + " Cookies für " + target.getName() + ". Neu: " + plugin.cookies().get(u)));
            }
            case "set" -> {
                int cur = plugin.cookies().get(u);
                if (amount >= cur) plugin.cookies().add(u, amount - cur);
                else plugin.cookies().take(u, cur - amount);
                sender.sendMessage(msg("Cookies von " + target.getName() + " auf " + plugin.cookies().get(u) + " gesetzt."));
            }
            default -> sender.sendMessage(msg("Unbekannte Aktion. Nutze add/remove/set."));
        }
        return true;
    }

    private String msg(String s) { return "§c§lWeihnachtsEvent: §r§7" + s; }
}
