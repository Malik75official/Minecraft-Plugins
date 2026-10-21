package de.skyfunity.weihnachten;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import java.net.URI;
import java.util.UUID;

public class AdventNpcCommand implements CommandExecutor {
    private final WeihnachtsPlugin plugin;
    private static final String SANTA_BASE64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDE2MWVhMjM2OWQxZGU5NjUzMmMwYjgxMWY5ODRjOTRiNTg0ZGVhOGRjNjFhOWFjYmM3ZTNiNzk2YzJlIn19fQ==";

    public AdventNpcCommand(WeihnachtsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur Spieler."); return true; }
        if (!p.hasPermission("Weihnachten.npc")) { p.sendMessage("Keine Berechtigung."); return true; }

        // Try Citizens (real player NPC) if available
        try {
            Class<?> cls = Class.forName("de.skyfunity.weihnachten.CitizensNpcHelper");
            java.lang.reflect.Method m = cls.getMethod("create", WeihnachtsPlugin.class, Player.class);
            Object result = m.invoke(null, plugin, p);
            if (Boolean.TRUE.equals(result)) return true;
        } catch (Throwable ignored) {}

        ArmorStand as = p.getWorld().spawn(p.getLocation(), ArmorStand.class);
        as.setCustomName("§4§l🎅 Weihnachtsmann 🎅");
        as.setCustomNameVisible(true);
        as.setInvulnerable(true);
        as.setGravity(false);
        as.setArms(true);
        as.setBasePlate(false);
        as.setSilent(true);

        // Santa head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.fromString("2bd03f26-1d31-4abe-b6c6-554e0925a537"), "Santa");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new java.net.URL("http://textures.minecraft.net/texture/d161ea2369d1de96532c0b811f984c94b584dea8dc61a9acbc7e3b796c2e"));
            profile.setTextures(textures);
            sm.setOwnerProfile(profile);
        } catch (Exception ignored) {}
        head.setItemMeta(sm);
        as.getEquipment().setHelmet(head);

        // red suit
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cm = (LeatherArmorMeta) chest.getItemMeta();
        cm.setColor(Color.fromRGB(180, 0, 0));
        chest.setItemMeta(cm);
        as.getEquipment().setChestplate(chest);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lm = (LeatherArmorMeta) legs.getItemMeta();
        lm.setColor(Color.fromRGB(180, 0, 0));
        legs.setItemMeta(lm);
        as.getEquipment().setLeggings(legs);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bm = (LeatherArmorMeta) boots.getItemMeta();
        bm.setColor(Color.fromRGB(0, 0, 0));
        boots.setItemMeta(bm);
        as.getEquipment().setBoots(boots);

        p.sendMessage("§aWeihnachtsmann-NPC (Spieler-Look) gesetzt.");
        return true;
    }
}