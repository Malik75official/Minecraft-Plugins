package de.malik.anticheat.checks.xray;

import de.malik.anticheat.AntiCheatPlugin;
import de.malik.anticheat.CheckType;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class XrayCheck implements Listener {
    private static final Material[] STONE_ORES = {
            Material.DIAMOND_ORE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.COPPER_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.EMERALD_ORE
    };
    private static final Material[] DEEPSLATE_ORES = {
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_COPPER_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.DEEPSLATE_LAPIS_ORE, Material.DEEPSLATE_EMERALD_ORE
    };
    private static final Material[] NETHER_ORES = {
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS
    };

    private final AntiCheatPlugin plugin;
    private final Map<UUID, OreStats> oreStats = new HashMap<>();
    private final Set<UUID> suspects = new HashSet<>();
    private final Map<UUID, Set<Location>> decoysByPlayer = new HashMap<>();

    public XrayCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void activateOreDecoys(Player player) {
        if (!player.hasPermission(CheckType.XRAY.getBypassPermission())
                && plugin.getConfig().getBoolean("checks.xray.decoys.enabled", true)
                && suspects.add(player.getUniqueId())) {
            refreshOreDecoys(player);
        }
    }

    public void refreshOreDecoys() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (suspects.contains(player.getUniqueId())) {
                refreshOreDecoys(player);
            }
        }
    }

    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            clearOreDecoys(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        Set<Location> playerDecoys = decoysByPlayer.get(player.getUniqueId());
        if (player.hasPermission(CheckType.XRAY.getBypassPermission())) {
            if (playerDecoys != null && playerDecoys.remove(location)) {
                player.sendBlockChange(location, event.getBlock().getBlockData());
            }
            return;
        }
        if (playerDecoys != null && playerDecoys.remove(location)) {
            event.setCancelled(true);
            player.sendBlockChange(location, event.getBlock().getBlockData());
            plugin.flag(player, CheckType.XRAY, "hat einen serverseitigen X-Ray-Köder abgebaut");
            return;
        }
        if (!plugin.getConfig().getBoolean("checks.xray.enabled", true)) {
            return;
        }

        Material type = event.getBlock().getType();
        boolean diamondOre = type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE;
        boolean ancientDebris = type == Material.ANCIENT_DEBRIS
                && plugin.getConfig().getBoolean("checks.xray.track-ancient-debris", true);
        if (!diamondOre && !ancientDebris) {
            return;
        }

        checkOrePattern(player, event.getBlock());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (suspects.contains(player.getUniqueId())
                && player.hasPermission(CheckType.XRAY.getBypassPermission())) {
            clearOreDecoys(player);
            suspects.remove(player.getUniqueId());
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getWorld() != to.getWorld()
                || from.getChunk().getX() != to.getChunk().getX()
                || from.getChunk().getZ() != to.getChunk().getZ())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> refreshOreDecoys(player));
        }
    }

    @EventHandler
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()
                    && plugin.getConfig().getBoolean("checks.xray.enabled", true)
                    && plugin.getConfig().getBoolean("checks.xray.decoys.enabled", true)
                    && !player.hasPermission(CheckType.XRAY.getBypassPermission())
                    && suspects.contains(player.getUniqueId())
                    && !hasDecoysInChunk(player.getUniqueId(), chunk)) {
                int perChunk = configuredOreCount();
                if (perChunk > 0 && chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) {
                    Set<Location> locations = decoysByPlayer.computeIfAbsent(player.getUniqueId(),
                            ignored -> new HashSet<>());
                    addChunkDecoys(player, chunk, perChunk, locations, ThreadLocalRandom.current());
                }
            }
        });
    }

    @EventHandler
    public void onChunkUnload(PlayerChunkUnloadEvent event) {
        Set<Location> locations = decoysByPlayer.get(event.getPlayer().getUniqueId());
        if (locations != null) {
            Chunk chunk = event.getChunk();
            locations.removeIf(location -> isInChunk(location, chunk));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        clearOreDecoys(event.getPlayer());
        suspects.remove(playerId);
        oreStats.remove(playerId);
    }

    private void checkOrePattern(Player player, Block block) {
        OreStats stats = oreStats.computeIfAbsent(player.getUniqueId(), ignored -> new OreStats());
        stats.total++;
        if (!isExposed(block)) {
            stats.hidden++;
        }

        int minimumOres = Math.max(1, plugin.getConfig().getInt("checks.xray.minimum-ores", 10));
        if (stats.total < minimumOres || stats.total % 5 != 0) {
            return;
        }
        double hiddenRatio = (double) stats.hidden / stats.total;
        double threshold = plugin.getConfig().getDouble("checks.xray.hidden-ore-ratio", 0.8);
        if (hiddenRatio >= threshold) {
            plugin.flag(player, CheckType.XRAY, String.format(java.util.Locale.ROOT,
                    "%.0f%% concealed ores (%d/%d)", hiddenRatio * 100, stats.hidden, stats.total));
        }
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : new BlockFace[]{
                BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        }) {
            if (block.getRelative(face).getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void refreshOreDecoys(Player player) {
        clearOreDecoys(player);
        if (!plugin.getConfig().getBoolean("checks.xray.enabled", true)
                || !plugin.getConfig().getBoolean("checks.xray.decoys.enabled", true)
                || player.hasPermission(CheckType.XRAY.getBypassPermission())
                || !suspects.contains(player.getUniqueId())) {
            suspects.remove(player.getUniqueId());
            return;
        }

        int radius = Math.max(0, Math.min(4,
                plugin.getConfig().getInt("checks.xray.decoys.chunk-radius", 2)));
        int perChunk = configuredOreCount();
        if (perChunk == 0) {
            return;
        }

        int centerX = player.getLocation().getChunk().getX();
        int centerZ = player.getLocation().getChunk().getZ();
        World world = player.getWorld();
        Set<Location> locations = decoysByPlayer.computeIfAbsent(player.getUniqueId(),
                ignored -> new HashSet<>());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    addChunkDecoys(player, world.getChunkAt(chunkX, chunkZ), perChunk, locations, random);
                }
            }
        }
    }

    private void addChunkDecoys(Player player, Chunk chunk, int count, Set<Location> locations,
                                ThreadLocalRandom random) {
        if (hasDecoysInChunk(locations, chunk)) {
            return;
        }
        World world = chunk.getWorld();
        int attempts = count * 100;
        for (int attempt = 0; attempt < attempts && count > 0; attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int y = random.nextInt(world.getMinHeight(), world.getMaxHeight());
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block block = world.getBlockAt(x, y, z);
            Material fakeOre = getDecoyMaterial(block.getType(), random);
            if (fakeOre == null) {
                continue;
            }

            Location location = block.getLocation();
            player.sendBlockChange(location, fakeOre.createBlockData());
            locations.add(location);
            count--;
        }
    }

    private Material getDecoyMaterial(Material base, ThreadLocalRandom random) {
        if (base == Material.STONE) {
            return STONE_ORES[random.nextInt(STONE_ORES.length)];
        }
        if (base == Material.DEEPSLATE) {
            return DEEPSLATE_ORES[random.nextInt(DEEPSLATE_ORES.length)];
        }
        if (base == Material.NETHERRACK) {
            return NETHER_ORES[random.nextInt(NETHER_ORES.length)];
        }
        return null;
    }

    private int configuredOreCount() {
        return Math.max(0, Math.min(64,
                plugin.getConfig().getInt("checks.xray.decoys.ores-per-chunk", 12)));
    }

    private boolean hasDecoysInChunk(UUID playerId, Chunk chunk) {
        Set<Location> locations = decoysByPlayer.get(playerId);
        return locations != null && hasDecoysInChunk(locations, chunk);
    }

    private boolean hasDecoysInChunk(Set<Location> locations, Chunk chunk) {
        return locations.stream().anyMatch(location -> isInChunk(location, chunk));
    }

    private boolean isInChunk(Location location, Chunk chunk) {
        return location.getWorld() == chunk.getWorld()
                && (location.getBlockX() >> 4) == chunk.getX()
                && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    private void clearOreDecoys(Player player) {
        Set<Location> locations = decoysByPlayer.remove(player.getUniqueId());
        if (locations == null) {
            return;
        }
        for (Location location : locations) {
            World world = location.getWorld();
            if (world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                player.sendBlockChange(location, world.getBlockAt(location).getBlockData());
            }
        }
    }

    private static final class OreStats {
        private int total;
        private int hidden;
    }
}
