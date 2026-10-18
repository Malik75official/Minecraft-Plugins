package de.malik.serversystem.caseopening;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import de.malik.serversystem.ServerSystemBootstrap;

public final class CaseAdminMenu implements Listener {
    private static final int MAX_REWARDS_IN_GUI = 27;
    private final ServerSystemBootstrap plugin;
    private final CaseOpeningManager manager;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public CaseAdminMenu(ServerSystemBootstrap plugin) {
        this.plugin = plugin;
        this.manager = plugin.getCaseOpeningManager();
    }

    public void openRoot(Player player) {
        FileConfiguration data = data();
        CaseMenuHolder holder = new CaseMenuHolder(CaseMenuHolder.Kind.ADMIN_ROOT, null, -1);
        Inventory inventory = createInventory(holder, 54, "§8Case-Verwaltung");

        int slot = 0;
        if (data.getConfigurationSection("cases") != null) {
        for (String caseId : data.getConfigurationSection("cases").getKeys(false)) {
            if (slot >= 45) {
                break;
            }
            String path = "cases." + caseId;
            Material material = materialAt(path + ".item-material", Material.CHEST);
            ItemStack item = makeItem(material, data.getString(path + ".display-name", caseId),
                    "§7ID: §f" + caseId, "§7Kristallpreis: §e" + data.getInt(path + ".price"),
                    "§aKlicken zum Bearbeiten");
            holder.bind(slot, caseId);
            inventory.setItem(slot++, item);
        }
        }

        inventory.setItem(49, makeItem(Material.EMERALD, "§aNeue Kiste erstellen", "§7Klicken und eine ID im Chat eingeben"));
        inventory.setItem(53, makeItem(Material.BARRIER, "§cSchließen"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CaseMenuHolder holder)
                || !isAdminMenu(holder.getKind())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!player.hasPermission("caseopening.admin")) {
            player.closeInventory();
            player.sendMessage("§cDeine Admin-Berechtigung wurde entfernt.");
            return;
        }

        switch (holder.getKind()) {
            case ADMIN_ROOT -> handleRootClick(player, holder, event.getSlot());
            case CASE_EDITOR -> handleCaseEditorClick(player, holder, event.getSlot());
            case REWARD_EDITOR -> handleRewardEditorClick(player, holder, event.getSlot());
            case DELETE_CONFIRMATION -> handleDeleteConfirmation(player, holder, event.getSlot());
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CaseMenuHolder holder
                && isAdminMenu(holder.getKind())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput pending = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline() && event.getPlayer().hasPermission("caseopening.admin")) {
                processInput(event.getPlayer(), pending, input);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    private void handleRootClick(Player player, CaseMenuHolder holder, int slot) {
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            prompt(player, new PendingInput(InputType.CREATE_CASE, null, -1, null),
                    "§eGib eine neue Case-ID ein (a-z, 0-9, _ oder -).");
            return;
        }
        String caseId = holder.getEntry(slot);
        if (caseId != null) {
            openCaseEditor(player, caseId);
        }
    }

    private void handleCaseEditorClick(Player player, CaseMenuHolder holder, int slot) {
        String caseId = holder.getCaseId();
        String path = "cases." + caseId;
        if (!data().isConfigurationSection(path)) {
            openRoot(player);
            return;
        }
        if (slot == 45) {
            openRoot(player);
        } else if (slot == 10) {
            prompt(player, new PendingInput(InputType.DISPLAY_NAME, caseId, -1, null), "§eGib den Case-Namen ein. &-Farbcodes sind erlaubt.");
        } else if (slot == 12) {
            prompt(player, new PendingInput(InputType.CASE_PRICE, caseId, -1, null), "§eGib den Kristallpreis als ganze Zahl ein.");
        } else if (slot == 14) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR || !hand.getType().isItem()) {
                player.sendMessage("§cHalte zuerst ein gültiges Item in der Haupthand.");
                return;
            }
            data().set(path + ".item-material", hand.getType().name());
            saveAndReload();
            openCaseEditor(player, caseId);
            player.sendMessage("§aCase-Icon aktualisiert.");
        } else if (slot == 16) {
            openDeleteConfirmation(player, caseId);
        } else if (slot == 48) {
            addItemReward(player, caseId);
        } else if (slot == 50) {
            addMoneyReward(player, caseId);
        } else {
            Integer rewardIndex = holder.getEntry(slot) == null ? null : Integer.valueOf(holder.getEntry(slot));
            if (rewardIndex != null) {
                openRewardEditor(player, caseId, rewardIndex);
            }
        }
    }

    private void handleRewardEditorClick(Player player, CaseMenuHolder holder, int slot) {
        String caseId = holder.getCaseId();
        int rewardIndex = holder.getRewardIndex();
        String path = rewardPath(caseId, rewardIndex);
        if (!data().isConfigurationSection("cases." + caseId)
                || rewardIndex >= data().getMapList("cases." + caseId + ".rewards").size()) {
            openCaseEditor(player, caseId);
            return;
        }
        if (slot == 18) {
            openCaseEditor(player, caseId);
        } else if (slot == 11) {
            prompt(player, new PendingInput(InputType.REWARD_CHANCE, caseId, rewardIndex, null),
                    "§eGib das neue Gewicht (Chance) als positive ganze Zahl ein.");
        } else if (slot == 15) {
            prompt(player, new PendingInput(InputType.REWARD_AMOUNT, caseId, rewardIndex, null),
                    "§eGib die neue Menge bzw. Geldsumme ein.");
        } else if (slot == 22) {
            List<Map<?, ?>> rewards = new ArrayList<>(data().getMapList("cases." + caseId + ".rewards"));
            rewards.remove(rewardIndex);
            data().set("cases." + caseId + ".rewards", rewards);
            saveAndReload();
            openCaseEditor(player, caseId);
        } else if (isMoneyReward(path)) {
            player.sendMessage("§7Geldbetrag anpassen: Slot §f15§7. Chance: Slot §f11§7.");
        }
    }

    private void handleDeleteConfirmation(Player player, CaseMenuHolder holder, int slot) {
        if (slot == 11) {
            data().set("cases." + holder.getCaseId(), null);
            saveAndReload();
            openRoot(player);
            player.sendMessage("§aCase §f" + holder.getCaseId() + " §awurde gelöscht.");
        } else if (slot == 15 || slot == 22) {
            openCaseEditor(player, holder.getCaseId());
        }
    }

    private void openCaseEditor(Player player, String caseId) {
        FileConfiguration data = data();
        String path = "cases." + caseId;
        if (!data.isConfigurationSection(path)) {
            openRoot(player);
            return;
        }
        CaseMenuHolder holder = new CaseMenuHolder(CaseMenuHolder.Kind.CASE_EDITOR, caseId, -1);
        Inventory inventory = createInventory(holder, 54, "§8Case: " + caseId);
        inventory.setItem(10, makeItem(Material.NAME_TAG, "§bName ändern", "§7Aktuell: " + data.getString(path + ".display-name", caseId)));
        inventory.setItem(12, makeItem(Material.GOLD_INGOT, "§eKristallpreis", "§7Aktuell: §f" + data.getInt(path + ".price"), "§7Klicken zum Ändern"));
        inventory.setItem(14, makeItem(materialAt(path + ".item-material", Material.CHEST), "§dIcon setzen", "§7Nimm ein Item in die Haupthand"));
        inventory.setItem(16, makeItem(Material.TNT, "§cKiste löschen", "§7Öffnet eine Bestätigung"));

        List<Map<?, ?>> rewards = data.getMapList(path + ".rewards");
        for (int index = 0; index < Math.min(rewards.size(), MAX_REWARDS_IN_GUI); index++) {
            Map<?, ?> reward = rewards.get(index);
            boolean money = String.valueOf(reward.get("type")).equalsIgnoreCase("money");
            Material icon = money ? Material.GOLD_NUGGET : materialAtValue(reward.get("item"), Material.STONE);
            Object rewardName = reward.containsKey("display-name") ? reward.get("display-name") : reward.get("item");
            String title = money ? "§6Geld: " + reward.get("amount") : String.valueOf(rewardName);
            ItemStack item = makeItem(icon, title, "§7Chance-Gewicht: §f" + reward.get("chance"), "§aKlicken zum Bearbeiten");
            holder.bind(18 + index, Integer.toString(index));
            inventory.setItem(18 + index, item);
        }
        inventory.setItem(45, makeItem(Material.ARROW, "§fZurück"));
        inventory.setItem(48, makeItem(Material.ANVIL, "§aItem-Reward hinzufügen", "§7Item in der Haupthand", "§7Chance anschließend im Chat"));
        inventory.setItem(50, makeItem(Material.GOLD_NUGGET, "§6Geld-Reward hinzufügen", "§7Betrag und Chance im Chat eingeben"));
        player.openInventory(inventory);
    }

    private void openRewardEditor(Player player, String caseId, int rewardIndex) {
        String path = rewardPath(caseId, rewardIndex);
        boolean money = isMoneyReward(path);
        Material material = money ? Material.GOLD_INGOT : materialAt(path + ".item", Material.STONE);
        Object amount = data().get(path + ".amount", 1);
        CaseMenuHolder holder = new CaseMenuHolder(CaseMenuHolder.Kind.REWARD_EDITOR, caseId, rewardIndex);
        Inventory inventory = createInventory(holder, 27, "§8Reward bearbeiten");
        inventory.setItem(13, makeItem(material, money ? "§6Geld: " + amount : "§f" + amount + "x " + data().getString(path + ".item"),
                "§7Chance-Gewicht: §f" + data().getInt(path + ".chance")));
        inventory.setItem(11, makeItem(Material.CLOCK, "§eChance ändern", "§7Höheres Gewicht = häufiger"));
        inventory.setItem(15, makeItem(Material.SUNFLOWER, money ? "§6Geldsumme ändern" : "§bItemmenge ändern"));
        inventory.setItem(18, makeItem(Material.ARROW, "§fZurück"));
        inventory.setItem(22, makeItem(Material.BARRIER, "§cReward entfernen"));
        player.openInventory(inventory);
    }

    private void openDeleteConfirmation(Player player, String caseId) {
        CaseMenuHolder holder = new CaseMenuHolder(CaseMenuHolder.Kind.DELETE_CONFIRMATION, caseId, -1);
        Inventory inventory = createInventory(holder, 27, "§cKiste löschen?");
        inventory.setItem(11, makeItem(Material.RED_CONCRETE, "§cJa, löschen", "§7" + caseId));
        inventory.setItem(15, makeItem(Material.GREEN_CONCRETE, "§aAbbrechen"));
        player.openInventory(inventory);
    }

    private void addItemReward(Player player, String caseId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !hand.getType().isItem()) {
            player.sendMessage("§cHalte das gewünschte Reward-Item in der Haupthand.");
            return;
        }
        if (!hasRewardSpace(caseId, player)) {
            return;
        }
        prompt(player, new PendingInput(InputType.ADD_ITEM_REWARD, caseId, -1, hand.clone()),
                "§eGib das Chance-Gewicht für dieses Item als positive ganze Zahl ein.");
    }

    private void addMoneyReward(Player player, String caseId) {
        if (!hasRewardSpace(caseId, player)) {
            return;
        }
        prompt(player, new PendingInput(InputType.ADD_MONEY_REWARD, caseId, -1, null),
                "§eGib Geldbetrag und Chance ein, z. B. §f250.00 10§e.");
    }

    private boolean hasRewardSpace(String caseId, Player player) {
        if (data().getMapList("cases." + caseId + ".rewards").size() >= MAX_REWARDS_IN_GUI) {
            player.sendMessage("§cDas GUI kann maximal " + MAX_REWARDS_IN_GUI + " Rewards bearbeiten.");
            return false;
        }
        return true;
    }

    private void prompt(Player player, PendingInput input, String message) {
        pendingInputs.put(player.getUniqueId(), input);
        player.closeInventory();
        player.sendMessage(message);
        player.sendMessage("§7Schreibe §cabbrechen §7zum Verwerfen.");
    }

    private void processInput(Player player, PendingInput pending, String input) {
        if (input.equalsIgnoreCase("abbrechen")) {
            reopen(player, pending);
            return;
        }
        try {
            FileConfiguration data = data();
            String casePath = "cases." + pending.caseId();
            switch (pending.type()) {
                case CREATE_CASE -> createCase(player, input);
                case DISPLAY_NAME -> data.set(casePath + ".display-name", input.replace('&', '§'));
                case CASE_PRICE -> data.set(casePath + ".price", parsePositiveInteger(input, true));
                case ADD_ITEM_REWARD -> addItemRewardToConfig(pending, parsePositiveInteger(input, false));
                case ADD_MONEY_REWARD -> addMoneyRewardToConfig(pending.caseId(), input);
                case REWARD_CHANCE -> updateRewardProperty(pending.caseId(), pending.rewardIndex(), "chance",
                    parsePositiveInteger(input, false));
                case REWARD_AMOUNT -> updateRewardAmount(pending, input);
            }
            if (pending.type() != InputType.CREATE_CASE) {
                saveAndReload();
                player.sendMessage("§aCase-Einstellung gespeichert.");
                reopen(player, pending);
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
            prompt(player, pending, "§eBitte gib einen gültigen Wert ein:");
        }
    }

    private void createCase(Player player, String rawId) {
        String caseId = rawId.toLowerCase(Locale.ROOT);
        if (!caseId.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Die ID darf nur a-z, 0-9, _ und - enthalten (max. 32 Zeichen).");
        }
        String path = "cases." + caseId;
        if (data().isConfigurationSection(path)) {
            throw new IllegalArgumentException("Diese Case-ID existiert bereits.");
        }
        data().set(path + ".display-name", "&a" + caseId);
        data().set(path + ".price", 0);
        data().set(path + ".item-material", Material.CHEST.name());
        data().set(path + ".lore", List.of());
        data().set(path + ".rewards", List.of());
        saveAndReload();
        player.sendMessage("§aCase erstellt. Name, Preis und Rewards kannst du jetzt einstellen.");
        openCaseEditor(player, caseId);
    }

    private void addItemRewardToConfig(PendingInput pending, int chance) {
        ItemStack item = pending.item();
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", "item");
        reward.put("item", item.getType().name());
        reward.put("amount", item.getAmount());
        reward.put("chance", chance);
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
            enchantments.put(enchantment.getKey().getKey().toString(), enchantment.getValue());
        }
        if (!enchantments.isEmpty()) {
            reward.put("enchantments", enchantments);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            reward.put("display-name", meta.getDisplayName());
        }
        appendReward(pending.caseId(), reward);
    }

    private void addMoneyRewardToConfig(String caseId, String input) {
        String[] parts = input.replace(',', '.').trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Format: Geldbetrag Chance, zum Beispiel 250.00 10.");
        }
        double amount = Double.parseDouble(parts[0]);
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Der Geldbetrag muss größer als 0 sein.");
        }
        int chance = parsePositiveInteger(parts[1], false);
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", "money");
        reward.put("amount", amount);
        reward.put("chance", chance);
        appendReward(caseId, reward);
    }

    private void appendReward(String caseId, Map<String, Object> reward) {
        List<Map<?, ?>> rewards = new ArrayList<>(data().getMapList("cases." + caseId + ".rewards"));
        rewards.add(reward);
        data().set("cases." + caseId + ".rewards", rewards);
    }

    private void updateRewardAmount(PendingInput pending, String input) {
        String path = rewardPath(pending.caseId(), pending.rewardIndex());
        if (isMoneyReward(path)) {
            double amount = Double.parseDouble(input.replace(',', '.'));
            if (!Double.isFinite(amount) || amount <= 0) {
                throw new IllegalArgumentException("Der Geldbetrag muss größer als 0 sein.");
            }
            updateRewardProperty(pending.caseId(), pending.rewardIndex(), "amount", amount);
        } else {
            updateRewardProperty(pending.caseId(), pending.rewardIndex(), "amount", parsePositiveInteger(input, false));
        }
    }

    private void updateRewardProperty(String caseId, int rewardIndex, String property, Object value) {
        String rewardsPath = "cases." + caseId + ".rewards";
        List<Map<?, ?>> rewards = new ArrayList<>(data().getMapList(rewardsPath));
        if (rewardIndex < 0 || rewardIndex >= rewards.size()) {
            throw new IllegalArgumentException("Dieser Reward existiert nicht mehr.");
        }

        Map<String, Object> updatedReward = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rewards.get(rewardIndex).entrySet()) {
            if (entry.getKey() instanceof String key) {
                updatedReward.put(key, entry.getValue());
            }
        }
        updatedReward.put(property, value);

        List<Map<String, Object>> updatedRewards = new ArrayList<>(rewards.size());
        for (int index = 0; index < rewards.size(); index++) {
            if (index == rewardIndex) {
                updatedRewards.add(updatedReward);
            } else {
                Map<String, Object> rewardCopy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rewards.get(index).entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        rewardCopy.put(key, entry.getValue());
                    }
                }
                updatedRewards.add(rewardCopy);
            }
        }
        data().set(rewardsPath, updatedRewards);
    }

    private int parsePositiveInteger(String input, boolean allowZero) {
        try {
            int value = Integer.parseInt(input.trim());
            if (value < (allowZero ? 0 : 1)) {
                throw new IllegalArgumentException("Der Wert muss " + (allowZero ? "mindestens 0" : "größer als 0") + " sein.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Bitte gib eine ganze Zahl ein.");
        }
    }

    private void reopen(Player player, PendingInput pending) {
        if (pending.type() == InputType.CREATE_CASE) {
            openRoot(player);
        } else if (pending.type() == InputType.REWARD_CHANCE || pending.type() == InputType.REWARD_AMOUNT) {
            openRewardEditor(player, pending.caseId(), pending.rewardIndex());
        } else {
            openCaseEditor(player, pending.caseId());
        }
    }

    private void saveAndReload() {
        manager.getSettings().save();
        manager.reloadCases();
    }

    private FileConfiguration data() {
        return manager.getSettings().getData();
    }

    private String rewardPath(String caseId, int index) {
        return "cases." + caseId + ".rewards." + index;
    }

    private boolean isMoneyReward(String path) {
        return "money".equalsIgnoreCase(data().getString(path + ".type", "item"));
    }

    private Material materialAt(String path, Material fallback) {
        return materialAtValue(data().getString(path), fallback);
    }

    private Material materialAtValue(Object value, Material fallback) {
        Material material = value == null ? null : Material.matchMaterial(String.valueOf(value));
        return material == null || !material.isItem() ? fallback : material;
    }

    private Inventory createInventory(CaseMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private ItemStack makeItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name.replace('&', '§'));
            meta.setLore(List.of(lore).stream().map(line -> line.replace('&', '§')).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isAdminMenu(CaseMenuHolder.Kind kind) {
        return kind == CaseMenuHolder.Kind.ADMIN_ROOT || kind == CaseMenuHolder.Kind.CASE_EDITOR
                || kind == CaseMenuHolder.Kind.REWARD_EDITOR || kind == CaseMenuHolder.Kind.DELETE_CONFIRMATION;
    }

    private enum InputType {
        CREATE_CASE,
        DISPLAY_NAME,
        CASE_PRICE,
        ADD_ITEM_REWARD,
        ADD_MONEY_REWARD,
        REWARD_CHANCE,
        REWARD_AMOUNT
    }

    private record PendingInput(InputType type, String caseId, int rewardIndex, ItemStack item) {
    }
}