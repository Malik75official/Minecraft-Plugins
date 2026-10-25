package de.malik.serversystem.bank;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import de.malik.serversystem.economy.EconomySystem;
import de.malik.serversystem.storage.PersistentYamlStorage;
import de.malik.serversystem.storage.YamlStore;

public final class BankSystem implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final int MAX_DAILY_CATCH_UP = 365;

    private final JavaPlugin plugin;
    private final EconomySystem economy;
    private final YamlStore bankData;
    private final NamespacedKey bankNpcKey;
    private Object citizensIntegration;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public BankSystem(JavaPlugin plugin, EconomySystem economy, PersistentYamlStorage storage) {
        this.plugin = plugin;
        this.economy = economy;
        if (!storage.hasData("bank.yml") && !new java.io.File(plugin.getDataFolder(), "bank.yml").isFile()) {
            plugin.saveResource("bank.yml", false);
        }
        this.bankData = new YamlStore(plugin, "bank.yml", storage);
        this.bankNpcKey = new NamespacedKey(plugin, "bank_npc");
        try {
            Class<?> integrationClass = Class.forName("de.malik.serversystem.bank.BankCitizensIntegration");
            this.citizensIntegration = integrationClass.getConstructor(JavaPlugin.class, BankSystem.class)
                    .newInstance(plugin, this);
            integrationClass.getMethod("register").invoke(this.citizensIntegration);
        } catch (Throwable ignored) {
            this.citizensIntegration = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur ingame verwendet werden.");
            return true;
        }
        return handleCommand(player, command.getName(), args);
    }

    private boolean handleCommand(Player player, String commandName, String[] args) {
        if (commandName.equalsIgnoreCase("banknpc")) {
            if (!player.hasPermission("serversystem.command.bank.npc")) {
                player.sendMessage("§cDafür hast du keine Berechtigung.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("delete")) {
                deleteNpc(player);
                return true;
            }
            spawnNpc(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("serversystem.command.bank.admin")) {
                player.sendMessage("§cDafür hast du keine Berechtigung.");
                return true;
            }
            openAdminMenu(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("npc")) {
            if (!player.hasPermission("serversystem.command.bank.npc")) {
                player.sendMessage("§cDafür hast du keine Berechtigung.");
                return true;
            }
            spawnNpc(player);
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("Verwendung: /bank, /bank admin oder /bank npc");
            return true;
        }
        openPlayerMenu(player);
        return true;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!event.getRightClicked().getPersistentDataContainer().has(bankNpcKey, PersistentDataType.BYTE)) {
            return;
        }
        event.setCancelled(true);
        openPlayerMenu(event.getPlayer());
    }

    @EventHandler
    public void onNpcDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(bankNpcKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BankMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (holder.view() == BankMenuHolder.View.ADMIN && !player.hasPermission("serversystem.command.bank.admin")) {
            player.closeInventory();
            player.sendMessage("§cDeine Bank-Admin-Berechtigung fehlt.");
            return;
        }
        switch (holder.view()) {
            case PLAYER -> handlePlayerClick(player, event.getSlot());
            case LEADERBOARD -> {
                if (event.getSlot() == 49) {
                    openPlayerMenu(player);
                }
            }
            case ADMIN -> handleAdminClick(player, event.getSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        PendingInput pending = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                processInput(player, pending, input);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bank")) {
            if (args.length == 1) {
                return java.util.List.of("admin", "npc");
            }
        }
        if (command.getName().equalsIgnoreCase("banknpc")) {
            if (args.length == 1) {
                return java.util.List.of("delete");
            }
        }
        return java.util.List.of();
    }

    public void shutdown() {
        bankData.save();
    }

    private void spawnNpc(Player player) {
        if (citizensIntegration != null) {
            try {
                boolean spawned = (Boolean) citizensIntegration.getClass()
                        .getMethod("spawnNpc", Player.class).invoke(citizensIntegration, player);
                if (spawned) {
                    return;
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Citizens-Bank-NPC konnte nicht erstellt werden: " + throwable.getMessage());
            }
        }

        Villager npc = player.getWorld().spawn(player.getLocation(), Villager.class, villager -> {
            villager.setCustomName("§6§lBank");
            villager.setCustomNameVisible(true);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setAI(false);
            villager.setCollidable(false);
            villager.setPersistent(true);
            villager.getPersistentDataContainer().set(bankNpcKey, PersistentDataType.BYTE, (byte) 1);
        });
        ensureBankerAccount(npc.getUniqueId());
        player.sendMessage("§aBank-NPC platziert: " + npc.getUniqueId());
    }

    public void ensureBankerAccountPublic(UUID bankerId) {
        ensureBankerAccount(bankerId);
    }

    private void deleteNpc(Player player) {
        if (citizensIntegration != null) {
            try {
                boolean deleted = (Boolean) citizensIntegration.getClass()
                        .getMethod("deleteNpc", Player.class).invoke(citizensIntegration, player);
                if (deleted) {
                    player.sendMessage("§aBank-NPC entfernt.");
                    return;
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Citizens-Bank-NPC konnte nicht entfernt werden: " + throwable.getMessage());
            }
        }

        org.bukkit.util.RayTraceResult rayResult = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getLocation().getDirection(), 5.0, 0.0,
                this::isBankNpcEntity);
        Entity target = rayResult == null ? null : rayResult.getHitEntity();

        if (target == null) {
            List<Entity> candidates = new ArrayList<>();
            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                if (isBankNpcEntity(entity)) {
                    candidates.add(entity);
                }
            }
            if (candidates.isEmpty()) {
                player.sendMessage("§cKein Bank-NPC in der Nähe gefunden.");
                return;
            }
            target = candidates.stream()
                    .min(java.util.Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                    .orElse(null);
        }

        if (target != null) {
            target.remove();
            player.sendMessage("§aBank-NPC entfernt.");
        } else {
            player.sendMessage("§cKein Bank-NPC in der Nähe gefunden.");
        }
    }

    private boolean isBankNpcEntity(Entity entity) {
        if (entity instanceof Villager villager
                && villager.getPersistentDataContainer().has(bankNpcKey, PersistentDataType.BYTE)) {
            return true;
        }
        if (citizensIntegration != null) {
            try {
                Object npc = citizensIntegration.getClass().getMethod("getNpc", Entity.class).invoke(citizensIntegration, entity);
                return npc != null;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private void ensureBankerAccount(UUID bankerId) {
        String path = accountPath(bankerId);
        if (bankData.data().contains(path + ".last-accrual")) {
            return;
        }
        bankData.data().set(path + ".balance", 0.0);
        bankData.data().set(path + ".last-accrual", System.currentTimeMillis());
        bankData.data().set(path + ".last-interest-amount", 0.0);
        bankData.data().set(path + ".last-interest-percent", 0.0);
        bankData.data().set(path + ".last-fee", 0.0);
        bankData.data().set(path + ".name", "Bänker");
        try {
            bankData.save();
        } catch (RuntimeException ignored) {
        }
    }

    public void openPlayerMenu(Player player) {
        accrueAccount(player.getUniqueId());
        BankMenuHolder holder = new BankMenuHolder(BankMenuHolder.View.PLAYER);
        Inventory inventory = createInventory(holder, 27, "§8Bank");
        UUID playerId = player.getUniqueId();
        String accountPath = accountPath(playerId);
        double balance = bankData.data().getDouble(accountPath + ".balance", 0);
        double wallet = economy.getBalance(playerId);
        double lastInterest = bankData.data().getDouble(accountPath + ".last-interest-amount", 0);
        double lastPercent = bankData.data().getDouble(accountPath + ".last-interest-percent", 0);
        double lastFee = bankData.data().getDouble(accountPath + ".last-fee", 0);
        double dailyRate = bankData.data().getDouble("settings.interest.daily-percent", 0.1);
        boolean feeEnabled = bankData.data().getBoolean("settings.account-fee.enabled", false);
        double dailyFee = bankData.data().getDouble("settings.account-fee.daily-amount", 0);

        inventory.setItem(11, menuItem(Material.GOLD_INGOT, "§6Kontostand", "§7" + economy.formatMoney(balance)));
        inventory.setItem(13, menuItem(Material.PAPER, "§eBargeld", "§7" + economy.formatMoney(wallet)));
        inventory.setItem(15, menuItem(Material.HONEY_BOTTLE, "§aEinzahlen", "§7Klicken und Betrag im Chat eingeben"));
        inventory.setItem(16, menuItem(Material.SUNFLOWER, "§bAuszahlen", "§7Klicken und Betrag im Chat eingeben"));
        inventory.setItem(22, menuItem(Material.EXPERIENCE_BOTTLE, "§dZinsübersicht",
                "§7Letzte Abrechnung: §f" + economy.formatMoney(lastInterest) + " §7(§f" + formatPercent(lastPercent) + "%§7)",
                "§7Aktueller Tageszins: §f" + formatPercent(dailyRate) + "%",
                feeEnabled ? "§7Kontoführung: §f" + economy.formatMoney(dailyFee) + "§7/Tag"
                        : "§7Kontoführungsgebühr: §aAus",
                "§7Letzte Gebühr: §f" + economy.formatMoney(lastFee)));
        inventory.setItem(24, menuItem(Material.WRITABLE_BOOK, "§6Bestenliste", "§7Klicken zum Anzeigen"));
        if (player.hasPermission("serversystem.command.bank.admin")) {
            inventory.setItem(26, menuItem(Material.COMPARATOR, "§cBank-Verwaltung", "§7Admin-GUI öffnen"));
        }
        player.openInventory(inventory);
    }

    private void openLeaderboard(Player player) {
        ConfigurationSection accounts = bankData.data().getConfigurationSection("accounts");
        List<AccountEntry> entries = new ArrayList<>();
        if (accounts != null) {
            for (String id : accounts.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(id);
                    String path = accountPath(playerId);
                    entries.add(new AccountEntry(playerId, bankData.data().getDouble(path + ".balance", 0)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ungültige UUID in bank.yml: " + id);
                }
            }
        }
        entries.sort(Comparator.comparingDouble(AccountEntry::balance).reversed());

        BankMenuHolder holder = new BankMenuHolder(BankMenuHolder.View.LEADERBOARD);
        Inventory inventory = createInventory(holder, 54, "§8Bank-Bestenliste");
        int limit = Math.min(entries.size(), 45);
        for (int index = 0; index < limit; index++) {
            AccountEntry entry = entries.get(index);
            String storedName = bankData.data().getString(accountPath(entry.playerId()) + ".name");
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId());
            String playerName = storedName != null ? storedName
                    : (offlinePlayer.getName() == null
                            ? entry.playerId().toString().substring(0, 8)
                            : offlinePlayer.getName());
            Material icon = switch (index) {
                case 0 -> Material.GOLD_BLOCK;
                case 1 -> Material.IRON_BLOCK;
                case 2 -> Material.COPPER_BLOCK;
                default -> Material.PLAYER_HEAD;
            };
            inventory.setItem(index, menuItem(icon, "§e#" + (index + 1) + " §f" + playerName,
                    "§7Bankguthaben: §6" + economy.formatMoney(entry.balance())));
        }
        inventory.setItem(49, menuItem(Material.ARROW, "§fZurück"));
        player.openInventory(inventory);
    }

    private void openAdminMenu(Player player) {
        BankMenuHolder holder = new BankMenuHolder(BankMenuHolder.View.ADMIN);
        Inventory inventory = createInventory(holder, 27, "§8Bank-Verwaltung");
        FileConfiguration data = bankData.data();
        boolean interestEnabled = data.getBoolean("settings.interest.enabled", true);
        double dailyPercent = data.getDouble("settings.interest.daily-percent", 0.1);
        boolean feeEnabled = data.getBoolean("settings.account-fee.enabled", false);
        double dailyFee = data.getDouble("settings.account-fee.daily-amount", 0);

        inventory.setItem(10, menuItem(interestEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                interestEnabled ? "§aZinsen: Aktiv" : "§7Zinsen: Aus", "§7Klicken zum Umschalten"));
        inventory.setItem(12, menuItem(Material.EXPERIENCE_BOTTLE, "§dTageszins ändern",
                "§7Aktuell: §f" + formatPercent(dailyPercent) + "%", "§7Wert im Chat eingeben (0 bis 100)"));
        inventory.setItem(14, menuItem(feeEnabled ? Material.LIME_DYE : Material.GRAY_DYE,
                feeEnabled ? "§aKontoführungsgebühr: Aktiv" : "§7Kontoführungsgebühr: Aus",
                "§7Wird täglich vom Bankkonto abgezogen", "§7Klicken zum Umschalten"));
        inventory.setItem(16, menuItem(Material.GOLD_NUGGET, "§eGebühr ändern",
                "§7Aktuell: §f" + economy.formatMoney(dailyFee) + "§7/Tag",
                "§7Betrag im Chat eingeben (0 oder größer)"));
        inventory.setItem(22, menuItem(Material.ARROW, "§fZurück zur Bank"));
        player.openInventory(inventory);
    }

    private void handlePlayerClick(Player player, int slot) {
        switch (slot) {
            case 15 -> prompt(player, new PendingInput(InputType.DEPOSIT), "§eEinzahlungsbetrag eingeben:");
            case 16 -> prompt(player, new PendingInput(InputType.WITHDRAW), "§eAuszahlungsbetrag eingeben:");
            case 24 -> openLeaderboard(player);
            case 26 -> {
                if (player.hasPermission("serversystem.command.bank.admin")) {
                    openAdminMenu(player);
                }
            }
            default -> {
            }
        }
    }

    private void handleAdminClick(Player player, int slot) {
        FileConfiguration data = bankData.data();
        switch (slot) {
            case 10 -> {
                data.set("settings.interest.enabled", !data.getBoolean("settings.interest.enabled", true));
                bankData.save();
                openAdminMenu(player);
            }
            case 12 -> prompt(player, new PendingInput(InputType.INTEREST_RATE),
                    "§eNeuen Tageszins in Prozent eingeben (0 bis 100):");
            case 14 -> {
                data.set("settings.account-fee.enabled", !data.getBoolean("settings.account-fee.enabled", false));
                bankData.save();
                openAdminMenu(player);
            }
            case 16 -> prompt(player, new PendingInput(InputType.ACCOUNT_FEE),
                    "§eNeue tägliche Kontoführungsgebühr eingeben (0 oder größer):");
            case 22 -> openPlayerMenu(player);
            default -> {
            }
        }
    }

    private void prompt(Player player, PendingInput input, String message) {
        pendingInputs.put(player.getUniqueId(), input);
        player.closeInventory();
        player.sendMessage(message);
        player.sendMessage("§7Schreibe §cabbrechen §7zum Abbrechen.");
    }

    private void processInput(Player player, PendingInput input, String rawInput) {
        if (rawInput.equalsIgnoreCase("abbrechen")) {
            reopen(player, input);
            return;
        }
        try {
            switch (input.type()) {
                case DEPOSIT -> deposit(player, parseMoney(rawInput));
                case WITHDRAW -> withdraw(player, parseMoney(rawInput));
                case INTEREST_RATE -> {
                    double rate = parseNonNegativeMoney(rawInput);
                    if (rate > 100) {
                        throw new IllegalArgumentException("Der Tageszins darf höchstens 100 Prozent sein.");
                    }
                    bankData.data().set("settings.interest.daily-percent", rate);
                    bankData.save();
                    player.sendMessage("§aTageszins auf §f" + formatPercent(rate) + "% §agesetzt.");
                }
                case ACCOUNT_FEE -> {
                    double fee = parseNonNegativeMoney(rawInput);
                    bankData.data().set("settings.account-fee.daily-amount", fee);
                    bankData.save();
                    player.sendMessage("§aTägliche Kontoführungsgebühr auf §f" + economy.formatMoney(fee) + " §agesetzt.");
                }
            }
            reopen(player, input);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c" + exception.getMessage());
            prompt(player, input, "§eBitte gib einen gültigen Betrag ein:");
        }
    }

    private void deposit(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        accrueAccount(playerId);
        if (!economy.withdraw(player.getUniqueId(), amount)) {
            throw new IllegalArgumentException("Dein Bargeld reicht dafür nicht aus.");
        }
        String path = accountPath(playerId);
        double previous = bankData.data().getDouble(path + ".balance", 0);
        bankData.data().set(path + ".balance", round(previous + amount));
        try {
            bankData.save();
        } catch (RuntimeException exception) {
            bankData.data().set(path + ".balance", previous);
            economy.deposit(playerId, amount);
            throw new IllegalArgumentException("Die Einzahlung konnte nicht gespeichert werden. Es wurde nichts eingezahlt.");
        }
        player.sendMessage("§aEingezahlt: §f" + economy.formatMoney(amount));
    }

    private void withdraw(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        accrueAccount(playerId);
        String path = accountPath(playerId);
        double previous = bankData.data().getDouble(path + ".balance", 0);
        if (previous < amount) {
            throw new IllegalArgumentException("Dein Bankguthaben reicht dafür nicht aus.");
        }
        bankData.data().set(path + ".balance", round(previous - amount));
        try {
            bankData.save();
        } catch (RuntimeException exception) {
            bankData.data().set(path + ".balance", previous);
            throw new IllegalArgumentException("Die Auszahlung konnte nicht gespeichert werden.");
        }
        if (!economy.deposit(playerId, amount)) {
            bankData.data().set(path + ".balance", previous);
            bankData.save();
            throw new IllegalArgumentException("Das Bargeld konnte nicht ausgezahlt werden; dein Bankguthaben wurde wiederhergestellt.");
        }
        player.sendMessage("§aAusgezahlt: §f" + economy.formatMoney(amount));
    }

    private void accrueAccount(UUID playerId) {
        String path = accountPath(playerId);
        long now = System.currentTimeMillis();
        if (!bankData.data().contains(path + ".last-accrual")) {
            bankData.data().set(path + ".balance", bankData.data().getDouble(path + ".balance", 0));
            bankData.data().set(path + ".last-accrual", now);
            bankData.data().set(path + ".last-interest-amount", 0);
            bankData.data().set(path + ".last-interest-percent", 0);
            bankData.data().set(path + ".last-fee", 0);
            bankData.save();
            return;
        }

        long lastAccrual = bankData.data().getLong(path + ".last-accrual", now);
        long elapsedDays = Math.min((now - lastAccrual) / DAY_MILLIS, MAX_DAILY_CATCH_UP);
        if (elapsedDays <= 0) {
            return;
        }

        double startingBalance = bankData.data().getDouble(path + ".balance", 0);
        double balance = startingBalance;
        double totalInterest = 0;
        double totalFees = 0;
        double interestPercent = Math.max(0, Math.min(100,
                bankData.data().getDouble("settings.interest.daily-percent", 0.1)));
        boolean interestEnabled = bankData.data().getBoolean("settings.interest.enabled", true);
        boolean feeEnabled = bankData.data().getBoolean("settings.account-fee.enabled", false);
        double dailyFee = Math.max(0, bankData.data().getDouble("settings.account-fee.daily-amount", 0));

        for (long day = 0; day < elapsedDays; day++) {
            double dayInterest = interestEnabled ? round(balance * interestPercent / 100) : 0;
            balance = round(balance + dayInterest);
            totalInterest = round(totalInterest + dayInterest);
            double dayFee = feeEnabled ? Math.min(balance, dailyFee) : 0;
            balance = round(balance - dayFee);
            totalFees = round(totalFees + dayFee);
        }

        bankData.data().set(path + ".balance", balance);
        bankData.data().set(path + ".last-accrual", lastAccrual + elapsedDays * DAY_MILLIS);
        bankData.data().set(path + ".last-interest-amount", totalInterest);
        bankData.data().set(path + ".last-interest-percent",
                startingBalance <= 0 ? 0 : round(totalInterest * 100 / startingBalance));
        bankData.data().set(path + ".last-fee", totalFees);
        bankData.save();
    }

    private void reopen(Player player, PendingInput input) {
        if (input.type() == InputType.INTEREST_RATE || input.type() == InputType.ACCOUNT_FEE) {
            openAdminMenu(player);
        } else {
            openPlayerMenu(player);
        }
    }

    private double parseMoney(String input) {
        try {
            double amount = new BigDecimal(input.trim().replace(',', '.'))
                    .setScale(2, RoundingMode.UNNECESSARY).doubleValue();
            if (!Double.isFinite(amount) || amount <= 0) {
                throw new IllegalArgumentException("Der Betrag muss größer als 0 sein.");
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Bitte gib einen gültigen Betrag mit höchstens zwei Nachkommastellen ein.");
        }
    }

    private double parseNonNegativeMoney(String input) {
        try {
            double amount = new BigDecimal(input.trim().replace(',', '.'))
                    .setScale(2, RoundingMode.UNNECESSARY).doubleValue();
            if (!Double.isFinite(amount) || amount < 0) {
                throw new IllegalArgumentException("Der Wert darf nicht negativ sein.");
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Bitte gib einen gültigen Betrag mit höchstens zwei Nachkommastellen ein.");
        }
    }

    private Inventory createInventory(BankMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.bind(inventory);
        return inventory;
    }

    private ItemStack menuItem(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String accountPath(UUID playerId) {
        return "accounts." + playerId;
    }

    private double round(double amount) {
        if (!Double.isFinite(amount)) {
            return Double.MAX_VALUE;
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatPercent(double percent) {
        return String.format(Locale.ROOT, "%.2f", percent);
    }

    private record AccountEntry(UUID playerId, double balance) {
    }

    private enum InputType {
        DEPOSIT,
        WITHDRAW,
        INTEREST_RATE,
        ACCOUNT_FEE
    }

    private record PendingInput(InputType type) {
    }
}
