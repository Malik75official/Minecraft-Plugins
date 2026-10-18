package dev.codex.compressedblocks;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
        int maxPayloadBytes,
        boolean craftingEnabled,
        boolean preferVanillaRecipes,
        boolean decompressOnlyStackable,
        Set<Material> blockedMaterials
) {
    public PluginSettings {
        maxPayloadBytes = Math.clamp(maxPayloadBytes, 4_096, 1_048_576);
        blockedMaterials = Collections.unmodifiableSet(EnumSet.copyOf(blockedMaterials));
    }

    public static PluginSettings load(FileConfiguration config, Logger logger) {
        if (config.contains("max-compression-level")
                && config.getInt("max-compression-level") != CompressionMath.MAX_TIER) {
            logger.warning(
                    "max-compression-level wird ignoriert: "
                            + "CompressedBlocks verwendet fest die Stufen 1x bis 9x."
            );
        }

        Set<Material> blocked = EnumSet.noneOf(Material.class);
        for (String configuredName : config.getStringList("blocked-materials")) {
            Material material = Material.matchMaterial(configuredName.toUpperCase(Locale.ROOT));
            if (material == null) {
                logger.warning("Unbekanntes Material in blocked-materials: " + configuredName);
            } else {
                blocked.add(material);
            }
        }

        return new PluginSettings(
                config.getInt("max-payload-bytes", 262_144),
                config.getBoolean("crafting.enabled", true),
                config.getBoolean("crafting.prefer-vanilla-recipes", true),
                config.getBoolean("crafting.decompress-only-stackable", true),
                blocked
        );
    }

    public boolean allows(Material material) {
        return material != null && material.isItem() && !material.isAir() && !blockedMaterials.contains(material);
    }
}
