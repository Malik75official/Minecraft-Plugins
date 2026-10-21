package de.skyfunity.weihnachten;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HeadListener implements Listener {
    private final WeihnachtsPlugin plugin;
    public HeadListener(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (plugin.heads().isHead(e.getBlock().getLocation())) {
            plugin.heads().remove(e.getBlock().getLocation());
            e.getPlayer().sendMessage("§c§lWeihnachtsEvent: §r§7Weihnachtskopf entfernt.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (!plugin.heads().isHead(e.getClickedBlock().getLocation())) return;
        e.setCancelled(true);
        if (!plugin.heads().claim(e.getPlayer().getUniqueId(), e.getClickedBlock().getLocation())) {
            e.getPlayer().sendMessage("§c§lWeihnachtsEvent: §r§7Du hast diesen Kopf bereits gefunden.");
            return;
        }
        int amt = plugin.heads().amount(e.getClickedBlock().getLocation());
        plugin.cookies().add(e.getPlayer().getUniqueId(), amt);
        e.getPlayer().sendMessage("§c§lWeihnachtsEvent: §r§aDu hast " + amt + " Cookies gefunden!");
    }
}