package de.malik.serversystem.trade;

import de.malik.serversystem.caseopening.CrystalManager;
import de.malik.serversystem.economy.EconomySystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.Locale;

public final class TradeSystem implements CommandExecutor, Listener, TabCompleter {
    private final JavaPlugin plugin;
    private final EconomySystem economy;
    private final CrystalManager crystals;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();
    private final Set<UUID> pendingMoneyInputs = new HashSet<>();
    private final Set<UUID> pendingCrystalInputs = new HashSet<>();

    private static final int[] LEFT_SLOTS = {9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26};
    private static final int[] RIGHT_SLOTS = {36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53};
    private static final int CONFIRM_LEFT = 0;
    private static final int CONFIRM_RIGHT = 8;
    private static final int MONEY_LEFT = 1;
    private static final int MONEY_RIGHT = 7;
    private static final int CRYSTAL_LEFT = 2;
    private static final int CRYSTAL_RIGHT = 6;

    public TradeSystem(JavaPlugin plugin, EconomySystem economy, CrystalManager crystals) {
        this.plugin = plugin;
        this.economy = economy;
        this.crystals = crystals;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("Verwendung: /trade accept | /trade cancel | /trade money <Betrag>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> acceptTrade(player);
            case "cancel" -> cancelTrade(player);
            case "money" -> setMoney(player, args);
            default -> player.sendMessage("Unbekannter Subbefehl.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("accept", "cancel", "money");
        }
        return List.of();
    }

    @EventHandler
    public void onSneakTrade(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player requester = event.getPlayer();
        if (requester.equals(target)) {
            return;
        }
        pendingRequests.put(target.getUniqueId(), requester.getUniqueId());
        requester.sendMessage("§aHandelsanfrage an " + target.getName() + " gesendet.");
        Component accept = Component.text("[ANNEHMEN]").color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/trade accept"));
        Component deny = Component.text("[ABLEHNEN]").color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/trade cancel"));
        target.sendMessage(Component.text(requester.getName() + " möchte mit dir handeln. ").append(accept).append(Component.text(" ")).append(deny));
    }

    private void acceptTrade(Player player) {
        UUID requesterId = pendingRequests.remove(player.getUniqueId());
        if (requesterId == null) {
            player.sendMessage("§cKeine offene Handelsanfrage.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) {
            player.sendMessage("§cDer anfragende Spieler ist offline.");
            return;
        }
        startTrade(requester, player);
    }

    private void startTrade(Player a, Player b) {
        TradeSession session = new TradeSession(a, b);
        sessions.put(a.getUniqueId(), session);
        sessions.put(b.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(null, 54, "§8Handel: " + a.getName() + " ↔ " + b.getName());
        session.inventory = inv;
        fillTradeInventory(inv);
        updateConfirmButtons(session);
        updateMoneyDisplay(session, a);
        updateMoneyDisplay(session, b);
        updateCrystalDisplay(session, a);
        updateCrystalDisplay(session, b);
        a.openInventory(inv);
        b.openInventory(inv);
    }

    private void setMoney(Player player, String[] args) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§cDu bist nicht in einem Handel.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("Verwendung: /trade money <Betrag>");
            return;
        }
        try {
            double amount = Double.parseDouble(args[1]);
            if (amount < 0) {
                player.sendMessage("§cBetrag darf nicht negativ sein.");
                return;
            }
            if (session.confirmed.contains(player.getUniqueId())) {
                player.sendMessage("§cDu kannst während der Bestätigung kein Geld ändern.");
                return;
            }
            session.money.put(player.getUniqueId(), amount);
            updateMoneyDisplay(session, player);
            player.openInventory(session.inventory);
            player.sendMessage("§aGebotenes Geld: " + amount);
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültiger Betrag.");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getInventory() != session.inventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        UUID pid = player.getUniqueId();
        boolean left = session.a.getUniqueId().equals(pid);
        int[] ownSlots = left ? LEFT_SLOTS : RIGHT_SLOTS;
        int confirmSlot = left ? CONFIRM_LEFT : CONFIRM_RIGHT;

        if (session.confirmed.contains(pid)) {
            return;
        }

        if (slot == confirmSlot) {
            toggleConfirm(session, player);
            return;
        }
        int moneySlot = left ? MONEY_LEFT : MONEY_RIGHT;
        if (slot == moneySlot) {
            if (session.confirmed.contains(pid)) {
                return;
            }
            pendingMoneyInputs.add(pid);
            player.sendMessage("§eGib den Betrag im Chat ein (oder 'abbrechen').");
            return;
        }
        int crystalSlot = left ? CRYSTAL_LEFT : CRYSTAL_RIGHT;
        if (slot == crystalSlot) {
            if (session.confirmed.contains(pid)) {
                return;
            }
            pendingCrystalInputs.add(pid);
            player.sendMessage("§eGib die Kristall-Anzahl im Chat ein (oder 'abbrechen').");
            return;
        }

        boolean ownSlot = false;
        for (int s : ownSlots) {
            if (s == slot) {
                ownSlot = true;
                break;
            }
        }
        if (!ownSlot) {
            return;
        }

        // allow normal move within own area by cancelling top-level handling? We cancelled event, so manually move
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        event.getInventory().setItem(slot, cursor);
        player.setItemOnCursor(current);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getInventory() != session.inventory) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        boolean crystalInput = pendingCrystalInputs.remove(player.getUniqueId());
        boolean moneyInput = !crystalInput && pendingMoneyInputs.remove(player.getUniqueId());
        if (!crystalInput && !moneyInput) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (input.equalsIgnoreCase("abbrechen")) {
            player.sendMessage("§eAbgebrochen.");
            return;
        }
        try {
            if (crystalInput) {
                int amount = Integer.parseInt(input.trim());
                if (amount < 0) {
                    throw new NumberFormatException();
                }
                if (session.confirmed.contains(player.getUniqueId())) {
                    player.sendMessage("§cBestätigung läuft.");
                    return;
                }
                session.crystalsOffered.put(player.getUniqueId(), amount);
                updateCrystalDisplay(session, player);
                player.openInventory(session.inventory);
                player.sendMessage("§aGebotene Kristalle: " + amount);
            } else {
                double amount = Double.parseDouble(input.replace(',', '.'));
                if (amount < 0) {
                    throw new NumberFormatException();
                }
                if (session.confirmed.contains(player.getUniqueId())) {
                    player.sendMessage("§cBestätigung läuft.");
                    return;
                }
                session.money.put(player.getUniqueId(), amount);
                updateMoneyDisplay(session, player);
                player.openInventory(session.inventory);
                player.sendMessage("§aGebotenes Geld: " + amount);
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültiger Wert.");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        cancelTradeSession(session, "Handel abgebrochen.");
    }

    private void toggleConfirm(TradeSession session, Player player) {
        UUID pid = player.getUniqueId();
        if (session.confirmed.contains(pid)) {
            session.confirmed.remove(pid);
            cancelCountdown(session);
            updateConfirmButtons(session);
            player.sendMessage("§eBestätigung abgebrochen.");
            return;
        }
        session.confirmed.add(pid);
        updateConfirmButtons(session);
        if (session.confirmed.contains(session.a.getUniqueId()) && session.confirmed.contains(session.b.getUniqueId())) {
            startCountdown(session);
        }
    }

    private void startCountdown(TradeSession session) {
        cancelCountdown(session);
        session.countdown = 5;
        session.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!session.confirmed.contains(session.a.getUniqueId()) || !session.confirmed.contains(session.b.getUniqueId())) {
                cancelCountdown(session);
                return;
            }
            session.countdown--;
            updateConfirmButtons(session);
            if (session.countdown <= 0) {
                cancelCountdown(session);
                completeTrade(session);
            }
        }, 20L, 20L);
    }

    private void cancelCountdown(TradeSession session) {
        if (session.countdownTask != null) {
            session.countdownTask.cancel();
            session.countdownTask = null;
        }
        session.countdown = 5;
    }

    private void completeTrade(TradeSession session) {
        // check money
        double moneyA = session.money.getOrDefault(session.a.getUniqueId(), 0.0);
        double moneyB = session.money.getOrDefault(session.b.getUniqueId(), 0.0);
        if (moneyA > 0 && !economy.withdraw(session.a.getUniqueId(), moneyA)) {
            cancelTradeSession(session, "Nicht genug Geld bei " + session.a.getName());
            return;
        }
        if (moneyB > 0 && !economy.withdraw(session.b.getUniqueId(), moneyB)) {
            // refund A
            if (moneyA > 0) economy.deposit(session.a.getUniqueId(), moneyA);
            cancelTradeSession(session, "Nicht genug Geld bei " + session.b.getName());
            return;
        }

        // collect items
        List<ItemStack> itemsA = collectItems(session.inventory, LEFT_SLOTS);
        List<ItemStack> itemsB = collectItems(session.inventory, RIGHT_SLOTS);

        // give items
        for (ItemStack item : itemsB) {
            if (item != null) session.a.getInventory().addItem(item);
        }
        for (ItemStack item : itemsA) {
            if (item != null) session.b.getInventory().addItem(item);
        }

        int crystalsA = session.crystalsOffered.getOrDefault(session.a.getUniqueId(), 0);
        int crystalsB = session.crystalsOffered.getOrDefault(session.b.getUniqueId(), 0);
        if (crystalsA > 0 && !crystals.takeBalance(session.a.getUniqueId(), crystalsA)) {
            if (moneyA > 0) economy.deposit(session.a.getUniqueId(), moneyA);
            if (moneyB > 0) economy.deposit(session.b.getUniqueId(), moneyB);
            cancelTradeSession(session, "Nicht genug Kristalle bei " + session.a.getName());
            return;
        }
        if (crystalsB > 0 && !crystals.takeBalance(session.b.getUniqueId(), crystalsB)) {
            if (crystalsA > 0) crystals.addBalance(session.a.getUniqueId(), crystalsA);
            if (moneyA > 0) economy.deposit(session.a.getUniqueId(), moneyA);
            if (moneyB > 0) economy.deposit(session.b.getUniqueId(), moneyB);
            cancelTradeSession(session, "Nicht genug Kristalle bei " + session.b.getName());
            return;
        }
        if (crystalsA > 0) crystals.addBalance(session.b.getUniqueId(), crystalsA);
        if (crystalsB > 0) crystals.addBalance(session.a.getUniqueId(), crystalsB);

        // money transfer
        if (moneyA > 0) economy.deposit(session.b.getUniqueId(), moneyA);
        if (moneyB > 0) economy.deposit(session.a.getUniqueId(), moneyB);

        session.a.sendMessage("§aHandel abgeschlossen.");
        session.b.sendMessage("§aHandel abgeschlossen.");
        sessions.remove(session.a.getUniqueId());
        sessions.remove(session.b.getUniqueId());
        session.a.closeInventory();
        session.b.closeInventory();
    }

    private List<ItemStack> collectItems(Inventory inv, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            if (item != null) {
                items.add(item.clone());
                inv.setItem(slot, null);
            }
        }
        return items;
    }

    private void cancelTradeSession(TradeSession session, String reason) {
        // return items
        for (int slot : LEFT_SLOTS) {
            ItemStack item = session.inventory.getItem(slot);
            if (item != null) {
                session.a.getInventory().addItem(item);
                session.inventory.setItem(slot, null);
            }
        }
        for (int slot : RIGHT_SLOTS) {
            ItemStack item = session.inventory.getItem(slot);
            if (item != null) {
                session.b.getInventory().addItem(item);
                session.inventory.setItem(slot, null);
            }
        }
        cancelCountdown(session);
        session.a.sendMessage("§c" + reason);
        session.b.sendMessage("§c" + reason);
        sessions.remove(session.a.getUniqueId());
        sessions.remove(session.b.getUniqueId());
    }

    private void cancelTrade(Player player) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancelTradeSession(session, "Handel abgebrochen.");
            return;
        }
        // cancel pending request
        pendingRequests.remove(player.getUniqueId());
        player.sendMessage("§eHandelsanfrage entfernt.");
    }

    private void fillTradeInventory(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        for (int slot = 0; slot < 54; slot++) {
            if (slot == CONFIRM_LEFT || slot == CONFIRM_RIGHT || slot == MONEY_LEFT || slot == MONEY_RIGHT
                    || slot == CRYSTAL_LEFT || slot == CRYSTAL_RIGHT) {
                continue;
            }
            boolean offer = false;
            for (int s : LEFT_SLOTS) if (s == slot) offer = true;
            for (int s : RIGHT_SLOTS) if (s == slot) offer = true;
            if (!offer) {
                inv.setItem(slot, glass.clone());
            }
        }
    }

    private void updateConfirmButtons(TradeSession session) {
        boolean aConfirmed = session.confirmed.contains(session.a.getUniqueId());
        boolean bConfirmed = session.confirmed.contains(session.b.getUniqueId());
        ItemStack left = new ItemStack(aConfirmed ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        var leftMeta = left.getItemMeta();
        if (leftMeta != null) {
            leftMeta.setDisplayName("§" + (aConfirmed ? "a" : "c") + "Bestätigen" + (aConfirmed ? " (" + session.countdown + ")" : ""));
            left.setItemMeta(leftMeta);
        }
        session.inventory.setItem(CONFIRM_LEFT, left);
        ItemStack right = new ItemStack(bConfirmed ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        var rightMeta = right.getItemMeta();
        if (rightMeta != null) {
            rightMeta.setDisplayName("§" + (bConfirmed ? "a" : "c") + "Bestätigen" + (bConfirmed ? " (" + session.countdown + ")" : ""));
            right.setItemMeta(rightMeta);
        }
        session.inventory.setItem(CONFIRM_RIGHT, right);
    }

    private void updateCrystalDisplay(TradeSession session, Player player) {
        boolean left = session.a.getUniqueId().equals(player.getUniqueId());
        int slot = left ? CRYSTAL_LEFT : CRYSTAL_RIGHT;
        int amount = session.crystalsOffered.getOrDefault(player.getUniqueId(), 0);
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§dKristalle: " + amount);
            item.setItemMeta(meta);
        }
        session.inventory.setItem(slot, item);
    }

    private void updateMoneyDisplay(TradeSession session, Player player) {
        boolean left = session.a.getUniqueId().equals(player.getUniqueId());
        int slot = left ? MONEY_LEFT : MONEY_RIGHT;
        double amount = session.money.getOrDefault(player.getUniqueId(), 0.0);
        ItemStack paper = new ItemStack(Material.PAPER);
        var meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Geld: " + amount);
            paper.setItemMeta(meta);
        }
        session.inventory.setItem(slot, paper);
    }

    private static class TradeSession {
        final Player a;
        final Player b;
        Inventory inventory;
        final Map<UUID, Double> money = new HashMap<>();
        final Map<UUID, Integer> crystalsOffered = new HashMap<>();
        final Set<UUID> confirmed = new HashSet<>();
        int countdown = 5;
        org.bukkit.scheduler.BukkitTask countdownTask;
        TradeSession(Player a, Player b) {
            this.a = a;
            this.b = b;
        }
    }
}
