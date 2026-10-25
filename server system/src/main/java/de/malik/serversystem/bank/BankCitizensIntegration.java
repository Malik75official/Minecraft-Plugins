package de.malik.serversystem.bank;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class BankCitizensIntegration implements Listener {
    private static final String BANK_NPC_KEY = "serversystem:bank";

    private final JavaPlugin plugin;
    private final BankSystem bankSystem;

    public BankCitizensIntegration(JavaPlugin plugin, BankSystem bankSystem) {
        this.plugin = plugin;
        this.bankSystem = bankSystem;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean spawnNpc(Player player) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            return false;
        }
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            NPC npc = registry.createNPC(EntityType.PLAYER, "§6§lBänker");
            npc.data().setPersistent(BANK_NPC_KEY, true);
            npc.setProtected(true);
            try {
                SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
                skin.setSkinName("Banker", true);
            } catch (Throwable ignored) {
            }
            npc.spawn(player.getLocation());
            bankSystem.ensureBankerAccountPublic(npc.getUniqueId());
            player.sendMessage("§aBank-NPC (Citizens) platziert: " + npc.getUniqueId());
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Citizens-Bank-NPC konnte nicht erstellt werden: " + throwable.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (npc == null || !isBankNpc(npc)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getClicker();
        if (player != null) {
            bankSystem.openPlayerMenu(player);
        }
    }

    public boolean deleteNpc(Player player) {
        NPC target = null;
        try {
            org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(), player.getLocation().getDirection(), 5.0, 0.0, entity -> {
                        try {
                            Object registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
                            NPC npc = (NPC) registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, entity);
                            return npc != null && isBankNpc(npc);
                        } catch (Throwable ignored) {
                            return false;
                        }
                    });
            if (result != null && result.getHitEntity() != null) {
                Object registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
                target = (NPC) registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, result.getHitEntity());
            }
        } catch (Throwable ignored) {
        }

        if (target == null) {
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                try {
                    Object registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
                    NPC npc = (NPC) registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, entity);
                    if (npc != null && isBankNpc(npc)) {
                        target = npc;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (target != null) {
            target.destroy();
            return true;
        }
        return false;
    }

    public Object getNpc(Entity entity) {
        try {
            Object registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            NPC npc = (NPC) registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, entity);
            return (npc != null && isBankNpc(npc)) ? npc : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isBankNpc(NPC npc) {
        try {
            Object value = npc.data().get(BANK_NPC_KEY);
            if (value instanceof Boolean bool) {
                return bool;
            }
            return npc.data().has(BANK_NPC_KEY);
        } catch (Throwable ignored) {
            return false;
        }
    }
}