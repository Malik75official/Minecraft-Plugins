package de.skyfunity.weihnachten;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CookieShopAdminCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    public CookieShopAdminCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
        if (!p.hasPermission("Weihnachten.admin")) { p.sendMessage("Keine Berechtigung."); return true; }

        if (args.length == 0) {
            CookieShopAdminGui.open(plugin, p);
            return true;
        }

        if (args[0].equalsIgnoreCase("add") && args.length >= 2) {
            int day;
            try { day = Integer.parseInt(args[1]); } catch (NumberFormatException e) { p.sendMessage("Ungültiger Tag."); return true; }
            int price = args.length >= 3 ? parseInt(args[2], 1) : 1;
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) { p.sendMessage("Halte ein Item in der Hand."); return true; }
            java.util.List<ShopManager.ShopEntry> list = new java.util.ArrayList<>(plugin.shop().forDay(day));
            list.add(new ShopManager.ShopEntry(held.clone(), price));
            plugin.shop().setDay(day, list);
            p.sendMessage("§aItem zu Tag " + day + " hinzugefügt (Preis " + price + ").");
            return true;
        }

        if (args[0].equalsIgnoreCase("clear") && args.length >= 2) {
            int day;
            try { day = Integer.parseInt(args[1]); } catch (NumberFormatException e) { p.sendMessage("Ungültiger Tag."); return true; }
            plugin.shop().setDay(day, new java.util.ArrayList<>());
            p.sendMessage("§aTag " + day + " geleert.");
            return true;
        }

        if (args[0].equalsIgnoreCase("list") && args.length >= 2) {
            int day;
            try { day = Integer.parseInt(args[1]); } catch (NumberFormatException e) { p.sendMessage("Ungültiger Tag."); return true; }
            for (int i = 0; i < plugin.shop().forDay(day).size(); i++) {
                ShopManager.ShopEntry e = plugin.shop().forDay(day).get(i);
                p.sendMessage("§7" + i + ": " + e.item.getType() + " x" + e.item.getAmount() + " Preis " + e.price);
            }
            return true;
        }

        p.sendMessage("§eVerwendung: /cookieshop-admin [add <tag> <preis>|clear <tag>|list <tag>]");
        return true;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}