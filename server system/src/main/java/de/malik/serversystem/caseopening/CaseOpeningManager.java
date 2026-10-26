package de.malik.serversystem.caseopening;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import de.malik.serversystem.ServerSystemBootstrap;
import de.malik.serversystem.economy.EconomySystem;
import de.malik.serversystem.storage.PersistentYamlStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class CaseOpeningManager {

    private final ServerSystemBootstrap plugin;
    private final EconomySystem economy;
    private final CaseOpeningSettings settings;
    private final NamespacedKey caseKey;
    private final Map<String, CaseData> cases = new LinkedHashMap<>();
    private final Random random = new Random();

    public CaseOpeningManager(ServerSystemBootstrap plugin, EconomySystem economy, PersistentYamlStorage storage) {
        this.plugin = plugin;
        this.economy = economy;
        this.settings = new CaseOpeningSettings(plugin, storage);
        this.caseKey = new NamespacedKey(plugin, "caseopening_case_id");
        reloadCases();
    }

    public CaseOpeningSettings getSettings() {
        return settings;
    }

    public void reloadCases() {
        cases.clear();
        FileConfiguration config = settings.getData();
        ConfigurationSection section = config.getConfigurationSection("cases");
        if (section == null) {
            plugin.getLogger().warning("Keine Case-Definitionen in config.yml gefunden.");
            return;
        }

        for (String caseId : section.getKeys(false)) {
            ConfigurationSection caseSection = section.getConfigurationSection(caseId);
            if (caseSection == null) {
                continue;
            }

            String displayName = ChatColor.translateAlternateColorCodes('&', caseSection.getString("display-name", caseId));
            int price = caseSection.getInt("price", 0);
            Material material = Material.matchMaterial(caseSection.getString("item-material", "CHEST"));
            if (material == null) {
                material = Material.CHEST;
            }

            List<String> lore = new ArrayList<>();
            for (String raw : caseSection.getStringList("lore")) {
                lore.add(ChatColor.translateAlternateColorCodes('&', raw));
            }
            if (lore.isEmpty()) {
                lore.add("§7Preis: " + price + " Kristalle");
            }

            List<Reward> rewards = new ArrayList<>();
            for (Map<?, ?> map : caseSection.getMapList("rewards")) {
                Reward reward = Reward.fromMap(map);
                if (reward != null) {
                    rewards.add(reward);
                }
            }

            cases.put(caseId.toLowerCase(Locale.ROOT), new CaseData(caseId, displayName, price, material, lore, rewards));
        }
    }

    public List<CaseData> getCases() {
        return new ArrayList<>(cases.values());
    }

    public CaseData getCase(String id) {
        if (id == null) {
            return null;
        }
        return cases.get(id.toLowerCase(Locale.ROOT));
    }

    public NamespacedKey getCaseKey() {
        return caseKey;
    }

    public boolean isCaseItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(caseKey, PersistentDataType.STRING);
    }

    public String getCaseIdFromItem(ItemStack itemStack) {
        if (!isCaseItem(itemStack)) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(caseKey, PersistentDataType.STRING);
    }

    public ItemStack createCaseItem(String caseId, int amount) {
        CaseData caseData = getCase(caseId);
        if (caseData == null) {
            return null;
        }

        ItemStack item = new ItemStack(caseData.material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(caseData.displayName);
        List<String> lore = new ArrayList<>(caseData.lore);
        lore.add("§7Typ: " + caseData.id);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(caseKey, PersistentDataType.STRING, caseData.id);
        item.setItemMeta(meta);
        return item;
    }

    public Inventory createCaseSelectionInventory() {
        CaseMenuHolder holder = new CaseMenuHolder(CaseMenuHolder.Kind.SELECTION, null, -1);
        Inventory inventory = Bukkit.createInventory(holder, 9 * 3, "§8Case Auswahl");
        holder.setInventory(inventory);
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, glass.clone());
        }
        int slot = 10;
        for (CaseData caseData : cases.values()) {
            ItemStack item = createCaseItem(caseData.id, 1);
            if (item != null && slot < 17) {
                inventory.setItem(slot++, item);
            }
        }
        return inventory;
    }

    public void openCaseSelection(Player player) {
        if (!player.hasPermission("caseopening.case.use")) {
            player.sendMessage("§cDu hast keine Berechtigung für Case-Opening.");
            return;
        }

        player.openInventory(createCaseSelectionInventory());
    }

    public void openCase(Player player, String caseId, boolean payWithCrystals) {
        if (!player.hasPermission("caseopening.case.use")) {
            player.sendMessage("§cDu hast keine Berechtigung für Case-Opening.");
            return;
        }

        CaseData caseData = getCase(caseId);
        if (caseData == null) {
            player.sendMessage("§cDiese Kiste existiert nicht.");
            return;
        }

        Reward reward = caseData.pickRandomReward(random);
        if (reward == null) {
            player.sendMessage("§cDiese Kiste hat noch keine Rewards.");
            return;
        }

        if (payWithCrystals) {
            int balance = plugin.getCrystalManager().getBalance(player.getUniqueId());
            if (balance < caseData.price) {
                player.sendMessage("§cDu brauchst mindestens " + caseData.price + " Kristalle für diese Kiste.");
                return;
            }
            if (!plugin.getCrystalManager().takeBalance(player.getUniqueId(), caseData.price)) {
                player.sendMessage("§cDu hast nicht genug Kristalle.");
                return;
            }
        }

        int steps = Math.clamp(settings.getData().getInt("animation.steps", 28), 8, 120);
        List<ItemStack> reel = new ArrayList<>(steps + 9);
        for (int index = 0; index < steps + 9; index++) {
            reel.add(caseData.pickRandomReward(random).toItemStack(economy));
        }
        reel.set(steps + 4, reward.toItemStack(economy));

        CaseMenuHolder animationHolder = new CaseMenuHolder(CaseMenuHolder.Kind.ANIMATION, caseData.id, -1);
        Inventory animationInventory = Bukkit.createInventory(animationHolder, 9 * 3, "§8Lotterie läuft...");
        animationHolder.setInventory(animationInventory);
        ItemStack marker = createMarker();
        animationInventory.setItem(4, marker);
        animationInventory.setItem(22, marker);
        player.openInventory(animationInventory);

        animateCaseSpin(player, animationInventory, reel, reward, caseData, payWithCrystals, steps, 0);
    }

    private void animateCaseSpin(Player player, Inventory inventory, List<ItemStack> reel, Reward reward,
            CaseData caseData, boolean payWithCrystals, int steps, int frame) {
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(9 + slot, reel.get(frame + slot).clone());
        }

        ItemStack marker = createMarker();
        inventory.setItem(4, marker);
        inventory.setItem(22, marker);

        if (frame < steps) {
            long delay = animationDelay(frame, steps);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> animateCaseSpin(player, inventory, reel, reward, caseData, payWithCrystals, steps, frame + 1), delay);
            return;
        }

        ItemStack finalItem = reward.toItemStack(economy);
        ItemMeta finalMeta = finalItem.getItemMeta();
        if (finalMeta != null) {
                finalMeta.displayName(Component.text("GEWONNEN: ").color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD)
                    .append(reward.getDisplayNameComponent(economy)));
            finalItem.setItemMeta(finalMeta);
        }

        for (int i = 0; i < 9; i++) {
            inventory.setItem(9 + i, new ItemStack(Material.AIR));
        }
        inventory.setItem(13, finalItem);
        inventory.setItem(4, marker);
        inventory.setItem(22, marker);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                if (payWithCrystals) {
                    plugin.getCrystalManager().addBalance(player.getUniqueId(), caseData.price);
                }
                return;
            }
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
            }
            if (reward.isMoney()) {
                if (!economy.deposit(player.getUniqueId(), reward.getMoneyAmount())) {
                    player.sendMessage("§cDie Auszahlung konnte nicht gebucht werden. Bitte wende dich an einen Administrator.");
                    return;
                }
                player.sendMessage(Component.text("Du hast ").color(NamedTextColor.GREEN)
                    .append(reward.getDisplayNameComponent(economy))
                    .append(Component.text(" als Gewinn erhalten.").color(NamedTextColor.GREEN)));
            } else {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward.toItemStack(economy));
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage(Component.text("Du hast ").color(NamedTextColor.GREEN)
                    .append(reward.getDisplayNameComponent(economy))
                    .append(Component.text(" gewonnen!").color(NamedTextColor.GREEN)));
            }
        }, 30L);
    }

    private long animationDelay(int frame, int steps) {
        int fastEnd = steps * 4 / 10;
        int mediumEnd = steps * 7 / 10;
        int slowEnd = steps * 9 / 10;
        if (frame < fastEnd) {
            return Math.max(1, settings.getData().getLong("animation.fast-delay-ticks", 1));
        }
        if (frame < mediumEnd) {
            return Math.max(1, settings.getData().getLong("animation.medium-delay-ticks", 2));
        }
        if (frame < slowEnd) {
            return Math.max(1, settings.getData().getLong("animation.slow-delay-ticks", 3));
        }
        return Math.max(1, settings.getData().getLong("animation.final-delay-ticks", 5));
    }

    private ItemStack createMarker() {
        ItemStack marker = new ItemStack(Material.NETHER_STAR);
        ItemMeta markerMeta = marker.getItemMeta();
        if (markerMeta != null) {
            markerMeta.setDisplayName("§6§lGEWINN");
            marker.setItemMeta(markerMeta);
        }
        return marker;
    }

    public void setCasePrice(String caseId, int price) {
        CaseData caseData = getCase(caseId);
        if (caseData == null) {
            return;
        }

        settings.getData().set("cases." + caseId + ".price", Math.max(0, price));
        settings.save();
        reloadCases();
    }

    public String getCaseStatusList() {
        if (cases.isEmpty()) {
            return "§cKeine Kisten konfiguriert.";
        }

        StringBuilder builder = new StringBuilder("§aVerfügbare Kisten:\n");
        for (CaseData data : cases.values()) {
            builder.append("§7- §f").append(data.id).append(" §8(§6").append(data.price).append(" Kristalle§8)\n");
        }
        return builder.toString();
    }

    public static class CaseData {
        private final String id;
        private final String displayName;
        private final int price;
        private final Material material;
        private final List<String> lore;
        private final List<Reward> rewards;

        public CaseData(String id, String displayName, int price, Material material, List<String> lore, List<Reward> rewards) {
            this.id = id;
            this.displayName = displayName;
            this.price = price;
            this.material = material;
            this.lore = lore;
            this.rewards = rewards;
        }

        public String getId() {
            return id;
        }

        public Reward pickRandomReward(Random random) {
            if (rewards.isEmpty()) {
                return null;
            }

            long totalChance = 0;
            for (Reward reward : rewards) {
                totalChance += reward.chance;
            }

            long roll = random.nextLong(totalChance) + 1;
            long current = 0;
            for (Reward reward : rewards) {
                current += reward.chance;
                if (roll <= current) {
                    return reward;
                }
            }
            return rewards.get(rewards.size() - 1);
        }
    }

    public static class Reward {
        private final Material material;
        private final int amount;
        private final double moneyAmount;
        private final boolean money;
        private final int chance;
        private final String displayName;
        private final Map<Enchantment, Integer> enchantments;

        public Reward(Material material, int amount, double moneyAmount, boolean money, int chance, String displayName,
                Map<Enchantment, Integer> enchantments) {
            this.material = material;
            this.amount = amount;
            this.moneyAmount = moneyAmount;
            this.money = money;
            this.chance = chance;
            this.displayName = displayName;
            this.enchantments = Map.copyOf(enchantments);
        }

        public static Reward fromMap(Map<?, ?> map) {
            int chance = map.get("chance") instanceof Number number ? Math.max(1, number.intValue()) : 1;
            Object rawType = map.get("type");
            String type = rawType == null ? "item" : String.valueOf(rawType);
            if (type.equalsIgnoreCase("money")) {
                Object rawAmount = map.get("amount");
                double moneyAmount = rawAmount instanceof Number number ? number.doubleValue() : 0;
                if (!Double.isFinite(moneyAmount) || moneyAmount <= 0) {
                    return null;
                }
                return new Reward(null, 0, moneyAmount, true, chance, null, Map.of());
            }

            Object rawItem = map.containsKey("item") ? map.get("item") : "STONE";
            String itemName = String.valueOf(rawItem).toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(itemName);
            if (material == null) {
                return null;
            }

            int amount = 1;
            if (map.get("amount") instanceof Number number) {
                amount = number.intValue();
            }

            String displayName = map.containsKey("display-name") ? String.valueOf(map.get("display-name")) : null;
            Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
            Object rawEnchantments = map.get("enchantments");
            if (rawEnchantments instanceof Map<?, ?> configuredEnchantments) {
                for (Map.Entry<?, ?> entry : configuredEnchantments.entrySet()) {
                    String keyString = String.valueOf(entry.getKey());
                    if (keyString.startsWith("Enchantment[") && keyString.endsWith("]")) {
                        keyString = keyString.substring("Enchantment[".length(), keyString.length() - 1);
                    }
                    NamespacedKey key = NamespacedKey.fromString(keyString);
                    Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
                    if (enchantment != null && entry.getValue() instanceof Number level && level.intValue() > 0) {
                        enchantments.put(enchantment, level.intValue());
                    }
                }
            }
            return new Reward(material, Math.max(1, amount), 0, false, chance,
                    displayName == null ? null : ChatColor.translateAlternateColorCodes('&', displayName), enchantments);
        }

        public boolean isMoney() {
            return money;
        }

        public double getMoneyAmount() {
            return moneyAmount;
        }

        public int getChance() {
            return chance;
        }

        public int getAmount() {
            return amount;
        }

        public Component getDisplayNameComponent(EconomySystem economy) {
            if (money) {
                return Component.text(economy.formatMoney(moneyAmount));
            }
            if (displayName != null && !displayName.isBlank()) {
                return LegacyComponentSerializer.legacySection().deserialize(displayName);
            }
            return Component.translatable(material.translationKey());
        }

        public ItemStack toItemStack(EconomySystem economy) {
            Material rewardMaterial = money ? Material.GOLD_INGOT : material;
            ItemStack stack = new ItemStack(rewardMaterial, money ? 1 : amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && (money || displayName != null && !displayName.isBlank())) {
                meta.displayName(getDisplayNameComponent(economy));
                stack.setItemMeta(meta);
            }
            for (Map.Entry<Enchantment, Integer> enchantment : enchantments.entrySet()) {
                stack.addUnsafeEnchantment(enchantment.getKey(), enchantment.getValue());
            }
            return stack;
        }
    }
}
