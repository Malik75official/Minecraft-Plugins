package de.malik.serversystem.caseopening;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import de.malik.serversystem.ServerSystemBootstrap;

public class CaseListener implements Listener {

    private final ServerSystemBootstrap plugin;

    public CaseListener(ServerSystemBootstrap plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCasePlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String caseId = meta.getPersistentDataContainer().get(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING);
        if (caseId == null) {
            return;
        }

        BlockState state = event.getBlockPlaced().getState();
        if (state instanceof Barrel barrel) {
            barrel.getPersistentDataContainer().set(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING, caseId);
            barrel.update();
        } else if (state instanceof Chest chest) {
            chest.getPersistentDataContainer().set(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING, caseId);
            chest.update();
        }
    }

    @EventHandler
    public void onCaseInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (clickedBlock.getType() == Material.ENDER_CHEST
                && plugin.getCaseOpeningManager().getSettings().isGenericEnderChest(clickedBlock.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().openInventory(plugin.getCaseOpeningManager().createCaseSelectionInventory());
            return;
        }

        BlockState state = clickedBlock.getState();
        String caseId = null;

        if (state instanceof Barrel barrel) {
            caseId = barrel.getPersistentDataContainer().get(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING);
        } else if (state instanceof Chest chest) {
            caseId = chest.getPersistentDataContainer().get(plugin.getCaseOpeningManager().getCaseKey(), PersistentDataType.STRING);
        }

        if (caseId == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (CaseCommand.CASE_CONTAINER_MARKER.equals(caseId)) {
            player.openInventory(plugin.getCaseOpeningManager().createCaseSelectionInventory());
            return;
        }

        plugin.getCaseOpeningManager().openCase(player, caseId, true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CaseMenuHolder holder)
                || (holder.getKind() != CaseMenuHolder.Kind.SELECTION && holder.getKind() != CaseMenuHolder.Kind.ANIMATION)) {
            return;
        }
        event.setCancelled(true);
        if (holder.getKind() == CaseMenuHolder.Kind.ANIMATION
                || !(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String caseId = plugin.getCaseOpeningManager().getCaseIdFromItem(clicked);
        if (caseId == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> plugin.getCaseOpeningManager().openCase(player, caseId, true));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CaseMenuHolder holder
                && (holder.getKind() == CaseMenuHolder.Kind.SELECTION || holder.getKind() == CaseMenuHolder.Kind.ANIMATION)) {
            event.setCancelled(true);
        }
    }
}
