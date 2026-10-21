package de.skyfunity.weihnachten;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ShopListener implements Listener {
    private final WeihnachtsPlugin plugin;
    public ShopListener(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("Cookie-Shop") && !e.getView().getTitle().contains("Admin")) {
            e.setCancelled(true);
            CookieShopGui.handleClick(plugin, e);
        } else if (e.getView().getTitle().contains("Cookie-Shop-Admin")) {
            e.setCancelled(true);
            CookieShopAdminGui.handleClick(plugin, e);
        } else if (e.getView().getTitle().contains("Adventskalender")) {
            e.setCancelled(true);
            AdventCalendarGui.handleClick(plugin, e);
            return;
        } else if (e.getView().getTitle().contains("Weihnachts-Quests")) {
            e.setCancelled(true);
            QuestGui.handleClick(plugin, e);
        }
    }
}