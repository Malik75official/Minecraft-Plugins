package de.skyfunity.weihnachten;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class AdventNpcListener implements Listener {
    private final WeihnachtsPlugin plugin;
    public AdventNpcListener(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        String name = e.getRightClicked().getCustomName();
        boolean isNpc = e.getRightClicked() instanceof ArmorStand
                || (e.getRightClicked() instanceof Player && name != null && (name.contains("Weihnachtsmann") || name.contains("Adventskalender")));
        if (!isNpc) return;
        if (name == null || (!name.contains("Weihnachtsmann") && !name.contains("Adventskalender"))) return;
        e.setCancelled(true);
        AdventCalendarGui.open(plugin, e.getPlayer());
    }
}