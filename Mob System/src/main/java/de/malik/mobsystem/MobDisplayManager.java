package de.malik.mobsystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

final class MobDisplayManager implements Listener {
    private static final int HEALTH_BAR_SEGMENTS = 10;
    private static final int DAMAGE_DISPLAY_TICKS = 24;

    private final MobSystemPlugin plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey baseMaxHealthKey;
    private final Map<UUID, TextDisplay> healthDisplays = new HashMap<>();
    private final Map<UUID, String> displayedState = new HashMap<>();
    private final Set<TextDisplay> damageDisplays = new HashSet<>();

    MobDisplayManager(MobSystemPlugin plugin) {
        this.plugin = plugin;
        levelKey = new NamespacedKey(plugin, "mob_level");
        baseMaxHealthKey = new NamespacedKey(plugin, "mob_base_max_health");
    }

    void start() {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob) {
                    initializeMob(mob);
                    createHealthDisplay(mob);
                }
            }
        }

        Bukkit.getScheduler().runTaskTimer(plugin, this::updateHealthDisplays, 1L, 2L);
    }

    void shutdown() {
        healthDisplays.values().forEach(TextDisplay::remove);
        damageDisplays.forEach(TextDisplay::remove);
        healthDisplays.clear();
        displayedState.clear();
        damageDisplays.clear();
    }

    @EventHandler
    public void onMobSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            initializeMob(mob);
            createHealthDisplay(mob);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Mob mob) {
                initializeMob(mob);
                createHealthDisplay(mob);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Mob mob) {
                removeHealthDisplay(mob.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        removeHealthDisplay(mob.getUniqueId());
        int level = getLevel(mob);
        if (level <= 1) {
            return;
        }

        double xpPerLevel = Math.max(0.0, plugin.getConfig().getDouble("rewards.xp-per-level", 0.5));
        double xpMultiplier = 1.0 + (level - 1) * xpPerLevel;
        long scaledExperience = Math.round(event.getDroppedExp() * xpMultiplier);
        event.setDroppedExp((int) Math.min(Integer.MAX_VALUE, scaledExperience));

        double lootPerLevel = Math.max(0.0, plugin.getConfig().getDouble("rewards.loot-per-level", 0.25));
        double lootMultiplier = 1.0 + (level - 1) * lootPerLevel;
        addBonusLoot(event.getDrops(), lootMultiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getFinalDamage() <= 0.0) {
            return;
        }

        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        boolean critical = event.getDamager() == attacker && isCriticalHit(attacker);
        showDamage(mob, event.getFinalDamage(), critical);
    }

    private void updateHealthDisplays() {
        for (var entry : new HashMap<>(healthDisplays).entrySet()) {
            UUID mobId = entry.getKey();
            TextDisplay display = entry.getValue();
            Entity entity = Bukkit.getEntity(mobId);

            if (!(entity instanceof Mob mob) || mob.isDead() || !mob.isValid()) {
                removeHealthDisplay(mobId);
                continue;
            }

            String displayKey = getDisplayKey(mob);
            if (!displayKey.equals(displayedState.get(mobId))) {
                display.text(createHealthText(mob));
                displayedState.put(mobId, displayKey);
            }

            display.teleport(getHealthDisplayLocation(mob));
        }
    }

    private void createHealthDisplay(Mob mob) {
        if (!mob.isValid() || mob.isDead()) {
            return;
        }

        UUID mobId = mob.getUniqueId();
        TextDisplay existing = healthDisplays.get(mobId);
        if (existing != null && existing.isValid()) {
            return;
        }

        TextDisplay display = mob.getWorld().spawn(
                getHealthDisplayLocation(mob),
                TextDisplay.class,
                textDisplay -> configureDisplay(textDisplay, false)
        );
        healthDisplays.put(mobId, display);

        display.text(createHealthText(mob));
        displayedState.put(mobId, getDisplayKey(mob));
    }

    private Component createHealthText(Mob mob) {
        double maxHealth = Math.max(1.0, mob.getMaxHealth());
        double healthRatio = Math.max(0.0, Math.min(1.0, mob.getHealth() / maxHealth));
        int filledSegments = (int) Math.ceil(healthRatio * HEALTH_BAR_SEGMENTS);

        int level = getLevel(mob);
        NamedTextColor levelColor = level >= 4 ? NamedTextColor.GOLD
                : level >= 2 ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        Component text = Component.text("Lv. " + level, levelColor).append(Component.newline());
        for (int segment = 0; segment < HEALTH_BAR_SEGMENTS; segment++) {
            TextColor color = segment < filledSegments ? NamedTextColor.RED : NamedTextColor.DARK_GRAY;
            text = text.append(Component.text("■", color));
        }
        text = text.append(Component.text(
                " " + formatHealth(mob.getHealth()) + "/" + formatHealth(maxHealth),
                NamedTextColor.WHITE
        ));
        return text;
    }

    private String getDisplayKey(Mob mob) {
        return getLevel(mob) + ":" + formatHealth(mob.getHealth()) + "/" + formatHealth(mob.getMaxHealth());
    }

    private void initializeMob(Mob mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        if (data.has(levelKey, PersistentDataType.INTEGER)) {
            storeBaseMaxHealthIfMissing(mob);
            return;
        }

        int level = rollLevel();
        data.set(levelKey, PersistentDataType.INTEGER, level);
        storeBaseMaxHealthIfMissing(mob);
        applyLevelHealth(mob, level);
    }

    void setLevel(Mob mob, int level) {
        initializeMob(mob);
        mob.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        applyLevelHealth(mob, level);
        displayedState.remove(mob.getUniqueId());
    }

    private void storeBaseMaxHealthIfMissing(Mob mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        if (data.has(baseMaxHealthKey, PersistentDataType.DOUBLE)) {
            return;
        }

        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        int currentLevel = getLevel(mob);
        double healthPerLevel = Math.max(0.0, plugin.getConfig().getDouble("levels.health-per-level", 0.2));
        double levelMultiplier = 1.0 + (currentLevel - 1) * healthPerLevel;
        double baseMaxHealth = maxHealth.getBaseValue() / levelMultiplier;
        if (Double.isFinite(baseMaxHealth) && baseMaxHealth > 0.0) {
            data.set(baseMaxHealthKey, PersistentDataType.DOUBLE, baseMaxHealth);
        }
    }

    private void applyLevelHealth(Mob mob, int level) {
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        Double baseMaxHealth = mob.getPersistentDataContainer().get(baseMaxHealthKey, PersistentDataType.DOUBLE);
        if (baseMaxHealth == null || !Double.isFinite(baseMaxHealth) || baseMaxHealth <= 0.0) {
            return;
        }

        double currentMaxHealth = mob.getMaxHealth();
        double healthRatio = currentMaxHealth <= 0.0 ? 1.0 : mob.getHealth() / currentMaxHealth;
        double healthPerLevel = Math.max(0.0, plugin.getConfig().getDouble("levels.health-per-level", 0.2));
        double scaledMaxHealth = baseMaxHealth * (1.0 + (level - 1) * healthPerLevel);
        if (!Double.isFinite(scaledMaxHealth) || scaledMaxHealth <= 0.0) {
            plugin.getLogger().warning("Ungueltige maximale Gesundheit fuer Mob-Level " + level + ".");
            return;
        }
        maxHealth.setBaseValue(scaledMaxHealth);
        mob.setHealth(Math.min(scaledMaxHealth, Math.max(0.0, healthRatio * scaledMaxHealth)));
    }

    private int rollLevel() {
        double chance = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("levels.spawn-chance", 0.2)));
        int maxLevel = Math.max(1, plugin.getConfig().getInt("levels.max-level", 5));
        if (maxLevel <= 1 || ThreadLocalRandom.current().nextDouble() >= chance) {
            return 1;
        }
        return ThreadLocalRandom.current().nextInt(2, maxLevel + 1);
    }

    private int getLevel(Mob mob) {
        Integer level = mob.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return level == null ? 1 : Math.max(1, level);
    }

    private void addBonusLoot(List<ItemStack> drops, double multiplier) {
        if (multiplier <= 1.0 || drops.isEmpty()) {
            return;
        }

        List<ItemStack> bonusDrops = new ArrayList<>();
        for (ItemStack drop : drops) {
            double bonusAmount = drop.getAmount() * (multiplier - 1.0);
            int amount = (int) Math.floor(bonusAmount);
            if (ThreadLocalRandom.current().nextDouble() < bonusAmount - amount) {
                amount++;
            }

            while (amount > 0) {
                ItemStack bonus = drop.clone();
                int stackAmount = Math.min(amount, bonus.getMaxStackSize());
                bonus.setAmount(stackAmount);
                bonusDrops.add(bonus);
                amount -= stackAmount;
            }
        }
        drops.addAll(bonusDrops);
    }

    private void showDamage(Mob mob, double damage, boolean critical) {
        Location location = mob.getLocation().add(0.0, mob.getHeight() + 0.45, 0.0);
        TextDisplay display = mob.getWorld().spawn(
                location,
                TextDisplay.class,
                textDisplay -> configureDisplay(textDisplay, true)
        );
        TextColor color = critical ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        display.text(Component.text("-" + formatHealth(damage), color));
        damageDisplays.add(display);

        new BukkitRunnable() {
            private int age;

            @Override
            public void run() {
                if (!display.isValid() || age >= DAMAGE_DISPLAY_TICKS) {
                    display.remove();
                    damageDisplays.remove(display);
                    cancel();
                    return;
                }

                Location nextLocation = location.clone().add(0.0, age * 0.025, 0.0);
                display.teleport(nextLocation);
                display.setTextOpacity((byte) Math.max(15, 255 - age * 10));
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void configureDisplay(TextDisplay display, boolean damageDisplay) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setShadowed(false);
        display.setSeeThrough(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
        display.setPersistent(false);
        display.setViewRange(damageDisplay ? 0.8F : 1.0F);
    }

    private Location getHealthDisplayLocation(Mob mob) {
        return mob.getLocation().add(0.0, mob.getHeight() + 0.25, 0.0);
    }

    private void removeHealthDisplay(UUID mobId) {
        TextDisplay display = healthDisplays.remove(mobId);
        if (display != null && display.isValid()) {
            display.remove();
        }
        displayedState.remove(mobId);
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isCriticalHit(Player player) {
        return player.getFallDistance() > 0.0F
                && !player.isOnGround()
                && !player.isSprinting()
                && !player.isClimbing()
                && !player.isInWater()
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS)
                && !player.isInsideVehicle()
                && player.getAttackCooldown() >= 0.9F;
    }

    private String formatHealth(double value) {
        return String.format(Locale.US, "%.1f", Math.max(0.0, value));
    }
}
