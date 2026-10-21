package de.skyfunity.weihnachten;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public class CitizensNpcHelper {
    public static boolean create(WeihnachtsPlugin plugin, Player p) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Citizens") == null) return false;
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            NPC npc = registry.createNPC(EntityType.PLAYER, "§4§l🎅 Weihnachtsmann 🎅");
            npc.spawn(p.getLocation());
            try {
                SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
                skin.setSkinName("Santa");
            } catch (Exception ignored) {}
            npc.setProtected(true);
            p.sendMessage("§c§lWeihnachtsEvent: §r§7§aWeihnachtsmann-NPC (Citizens) gesetzt.");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}