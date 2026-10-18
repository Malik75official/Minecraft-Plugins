package de.malik.serversystem.utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PlayerAdministrationSystem implements CommandExecutor, Listener {
    private static final long TELEPORT_REQUEST_TICKS = 60L * 20L;

    private final JavaPlugin plugin;
    private final NamespacedKey lockedContainerKey;
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<UUID, TeleportRequest> requestsByTarget = new HashMap<>();
    private final Map<UUID, TeleportRequest> requestsByRequester = new HashMap<>();
    private final Map<UUID, UUID> inventoryViewTargets = new HashMap<>();

    public PlayerAdministrationSystem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lockedContainerKey = new NamespacedKey(plugin, "locked_container");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "fly" -> toggleFly(sender, args);
            case "i" -> giveItem(sender, args);
            case "vanish" -> toggleVanish(sender, args);
            case "gamemode", "gm" -> changeGameMode(sender, args);
            case "tp" -> teleport(sender, args, false);
            case "tpo" -> teleport(sender, args, true);
            case "tphere" -> teleportHere(sender, args);
            case "tpa" -> requestTeleport(sender, args, false);
            case "tpahere" -> requestTeleport(sender, args, true);
            case "tpaccept" -> acceptTeleport(sender, args);
            case "tpadeny" -> denyTeleport(sender, args);
            case "tpacancel" -> cancelTeleport(sender, args);
            case "invsee" -> inspectInventory(sender, args);
            case "lockchest" -> toggleContainerLock(sender, args);
            default -> sender.sendMessage("Unbekannter Befehl.");
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID vanishedId : vanishedPlayers) {
            Player vanished = Bukkit.getPlayer(vanishedId);
            if (vanished != null && !vanished.equals(joining)) {
                joining.hidePlayer(plugin, vanished);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (vanishedPlayers.remove(player.getUniqueId())) {
            showToEveryone(player);
        }
        cancelRequestForDisconnect(player);
        inventoryViewTargets.remove(player.getUniqueId());
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(inventoryViewTargets.entrySet())) {
            if (!entry.getValue().equals(player.getUniqueId())) {
                continue;
            }
            inventoryViewTargets.remove(entry.getKey(), entry.getValue());
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) {
                viewer.closeInventory();
                viewer.sendMessage("§eDie Inventaransicht wurde geschlossen, weil der Spieler offline gegangen ist.");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        UUID targetId = inventoryViewTargets.get(event.getWhoClicked().getUniqueId());
        if (targetId != null && !event.getWhoClicked().hasPermission("serversystem.command.invsee.edit")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        UUID targetId = inventoryViewTargets.get(event.getWhoClicked().getUniqueId());
        if (targetId != null && !event.getWhoClicked().hasPermission("serversystem.command.invsee.edit")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        UUID targetId = inventoryViewTargets.get(viewerId);
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target != null && event.getView().getTopInventory() == target.getInventory()) {
            inventoryViewTargets.remove(viewerId, targetId);
        }
    }

    @EventHandler
    public void onLockedContainerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (isLocked(event.getClickedBlock()) && !event.getPlayer().hasPermission("serversystem.command.lockchest")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDiese Kiste ist gesperrt.");
        }
    }

    @EventHandler
    public void onLockedContainerBreak(BlockBreakEvent event) {
        if (isLocked(event.getBlock()) && !event.getPlayer().hasPermission("serversystem.command.lockchest")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cDiese Kiste ist gesperrt und kann nicht abgebaut werden.");
        }
    }

    @EventHandler
    public void onLockedContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || event.getInventory().getLocation() == null) {
            return;
        }
        Block block = event.getInventory().getLocation().getBlock();
        if (isLocked(block) && !player.hasPermission("serversystem.command.lockchest")) {
            event.setCancelled(true);
            player.sendMessage("§cDiese Kiste ist gesperrt.");
        }
    }

    public void shutdown() {
        for (UUID vanishedId : new HashSet<>(vanishedPlayers)) {
            Player vanished = Bukkit.getPlayer(vanishedId);
            if (vanished != null) {
                showToEveryone(vanished);
            }
        }
        vanishedPlayers.clear();
    }

    private void toggleFly(CommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.sendMessage("Verwendung: /fly [Spieler]");
            return;
        }
        if (args.length == 0) {
            if (!require(sender, "serversystem.command.fly")) {
                return;
            }
            Player player = requirePlayer(sender);
            if (player != null) {
                setFlight(player, !player.getAllowFlight(), sender);
            }
            return;
        }
        if (!require(sender, "serversystem.command.fly.others")) {
            return;
        }
        Player target = findOnlinePlayer(sender, args[0]);
        if (target != null) {
            setFlight(target, !target.getAllowFlight(), sender);
        }
    }

    private void setFlight(Player target, boolean enabled, CommandSender sender) {
        target.setAllowFlight(enabled);
        if (!enabled) {
            target.setFlying(false);
        }
        target.sendMessage(enabled ? "§aFlugmodus aktiviert." : "§cFlugmodus deaktiviert.");
        if (!sender.equals(target)) {
            sender.sendMessage("§aFlugmodus für " + target.getName() + (enabled ? " aktiviert." : " deaktiviert."));
        }
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.item")) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("Verwendung: /i <Item> [Menge]");
            return;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isItem()) {
            sender.sendMessage("§cUnbekanntes Item: " + args[0]);
            return;
        }
        int amount = 1;
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cDie Menge muss eine ganze Zahl sein.");
                return;
            }
        }
        if (amount < 1) {
            sender.sendMessage("§cDie Menge muss mindestens 1 sein.");
            return;
        }

        int remaining = amount;
        int maxStackSize = material.getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStackSize);
            ItemStack stack = new ItemStack(material, stackAmount);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stackAmount;
        }
        sender.sendMessage("§a" + amount + "x " + material.name() + " erhalten.");
    }

    private void toggleVanish(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.vanish")) {
            return;
        }
        if (args.length != 0) {
            sender.sendMessage("Verwendung: /vanish");
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (vanishedPlayers.add(player.getUniqueId())) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(player)) {
                    viewer.hidePlayer(plugin, player);
                }
            }
            player.sendMessage("§aDu bist jetzt unsichtbar. Dein Nametag ist ebenfalls verborgen.");
        } else {
            vanishedPlayers.remove(player.getUniqueId());
            showToEveryone(player);
            player.sendMessage("§eDu bist wieder sichtbar.");
        }
    }

    private void changeGameMode(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.gamemode")) {
            return;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage("Verwendung: /gamemode <0|1|2|3|survival|creative|adventure|spectator> [Spieler]");
            return;
        }

        GameMode gameMode = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "0", "survival" -> GameMode.SURVIVAL;
            case "1", "creative" -> GameMode.CREATIVE;
            case "2", "adventure" -> GameMode.ADVENTURE;
            case "3", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
        if (gameMode == null) {
            sender.sendMessage("§cUngültiger Spielmodus. Erlaubt sind 0–3 oder survival, creative, adventure, spectator.");
            return;
        }

        Player target;
        if (args.length == 2) {
            if (!require(sender, "serversystem.command.gamemode.others")) {
                return;
            }
            target = findOnlinePlayer(sender, args[1]);
        } else {
            target = requirePlayer(sender);
        }
        if (target == null) {
            return;
        }

        target.setGameMode(gameMode);
        target.sendMessage("§aDein Spielmodus ist jetzt §f" + gameMode.name().toLowerCase(Locale.ROOT) + "§a.");
        if (!sender.equals(target)) {
            sender.sendMessage("§aSpielmodus von " + target.getName() + " auf §f"
                    + gameMode.name().toLowerCase(Locale.ROOT) + " §agesetzt.");
        }
    }

    private void teleport(CommandSender sender, String[] args, boolean silent) {
        String permission = silent ? "serversystem.command.tpo" : "serversystem.command.tp";
        if (!require(sender, permission)) {
            return;
        }
        if (args.length == 1) {
            Player source = requirePlayer(sender);
            Player target = findOnlinePlayer(sender, args[0]);
            if (source != null && target != null) {
                source.teleport(target.getLocation());
                source.sendMessage("§aTeleportiert zu " + target.getName() + ".");
            }
            return;
        }
        if (args.length == 2) {
            if (!require(sender, permission + ".others")) {
                return;
            }
            Player source = findOnlinePlayer(sender, args[0]);
            Player target = findOnlinePlayer(sender, args[1]);
            if (source != null && target != null) {
                source.teleport(target.getLocation());
                source.sendMessage("§aDu wurdest zu " + target.getName() + " teleportiert.");
                sender.sendMessage("§a" + source.getName() + " wurde zu " + target.getName() + " teleportiert.");
            }
            return;
        }
        sender.sendMessage(silent ? "Verwendung: /tpo <Spieler> [Spieler]" : "Verwendung: /tp <Spieler> [Ziel]");
    }

    private void teleportHere(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.tphere")) {
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /tphere <Spieler>");
            return;
        }
        Player destination = requirePlayer(sender);
        Player target = findOnlinePlayer(sender, args[0]);
        if (destination != null && target != null) {
            target.teleport(destination.getLocation());
            target.sendMessage("§aDu wurdest zu " + destination.getName() + " teleportiert.");
            sender.sendMessage("§a" + target.getName() + " wurde zu dir teleportiert.");
        }
    }

    private void requestTeleport(CommandSender sender, String[] args, boolean bringTarget) {
        String permission = bringTarget ? "serversystem.command.tpahere" : "serversystem.command.tpa";
        if (!require(sender, permission)) {
            return;
        }
        Player requester = requirePlayer(sender);
        if (requester == null) {
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(bringTarget ? "Verwendung: /tpahere <Spieler>" : "Verwendung: /tpa <Spieler>");
            return;
        }
        Player target = findOnlinePlayer(sender, args[0]);
        if (target == null) {
            return;
        }
        if (requester.equals(target)) {
            sender.sendMessage("§cDu kannst keine Anfrage an dich selbst senden.");
            return;
        }

        removeRequest(requestsByRequester.remove(requester.getUniqueId()));
        removeRequest(requestsByTarget.remove(target.getUniqueId()));
        TeleportRequest request = new TeleportRequest(requester.getUniqueId(), target.getUniqueId(), bringTarget,
                System.currentTimeMillis() + 60_000L);
        requestsByTarget.put(target.getUniqueId(), request);
        requestsByRequester.put(requester.getUniqueId(), request);
        sendClickableRequest(target, requester, bringTarget);
        requester.sendMessage("§aTeleportanfrage an " + target.getName() + " gesendet. Sie läuft in 60 Sekunden ab.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireRequest(request), TELEPORT_REQUEST_TICKS);
    }

    private void sendClickableRequest(Player target, Player requester, boolean bringTarget) {
        String action = bringTarget ? "möchte dich zu sich teleportieren" : "möchte sich zu dir teleportieren";
        Component accept = Component.text("[ANNEHMEN]")
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tpaccept " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Teleportanfrage annehmen")));
        Component deny = Component.text("[ABLEHNEN]")
                .color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/tpadeny " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Teleportanfrage ablehnen")));
        target.sendMessage(Component.text(requester.getName() + " " + action + ". ")
                .append(accept).append(Component.text(" ")).append(deny));
    }

    private void acceptTeleport(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.tpaccept")) {
            return;
        }
        Player recipient = requirePlayer(sender);
        if (recipient == null || args.length > 1) {
            sender.sendMessage("Verwendung: /tpaccept [Spieler]");
            return;
        }
        TeleportRequest request = requestsByTarget.get(recipient.getUniqueId());
        if (!matchesRequest(request, args)) {
            sender.sendMessage("§cDu hast keine passende offene Teleportanfrage.");
            return;
        }
        if (isExpired(request)) {
            removeRequest(request);
            sender.sendMessage("§cDiese Teleportanfrage ist abgelaufen.");
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            removeRequest(request);
            sender.sendMessage("§cDer anfragende Spieler ist nicht mehr online.");
            return;
        }
        removeRequest(request);
        Player moving = request.bringTarget() ? recipient : requester;
        Player destination = request.bringTarget() ? requester : recipient;
        moving.teleport(destination.getLocation());
        moving.sendMessage("§aTeleportanfrage angenommen.");
        if (!moving.equals(recipient)) {
            recipient.sendMessage("§aTeleportanfrage angenommen.");
        }
    }

    private void denyTeleport(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.tpadeny")) {
            return;
        }
        Player recipient = requirePlayer(sender);
        if (recipient == null || args.length > 1) {
            sender.sendMessage("Verwendung: /tpadeny [Spieler]");
            return;
        }
        TeleportRequest request = requestsByTarget.get(recipient.getUniqueId());
        if (!matchesRequest(request, args)) {
            sender.sendMessage("§cDu hast keine passende offene Teleportanfrage.");
            return;
        }
        removeRequest(request);
        sender.sendMessage("§eTeleportanfrage abgelehnt.");
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            requester.sendMessage("§c" + recipient.getName() + " hat deine Teleportanfrage abgelehnt.");
        }
    }

    private void cancelTeleport(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.tpacancel")) {
            return;
        }
        Player requester = requirePlayer(sender);
        if (requester == null || args.length != 0) {
            sender.sendMessage("Verwendung: /tpacancel");
            return;
        }
        TeleportRequest request = requestsByRequester.remove(requester.getUniqueId());
        if (request == null) {
            sender.sendMessage("§cDu hast keine offene Teleportanfrage.");
            return;
        }
        requestsByTarget.remove(request.recipient(), request);
        Player recipient = Bukkit.getPlayer(request.recipient());
        if (recipient != null) {
            recipient.sendMessage("§eDie Teleportanfrage von " + requester.getName() + " wurde zurückgezogen.");
        }
        sender.sendMessage("§aTeleportanfrage zurückgezogen.");
    }

    private void inspectInventory(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.invsee")) {
            return;
        }
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /invsee <Spieler>");
            return;
        }
        Player viewer = requirePlayer(sender);
        Player target = findOnlinePlayer(sender, args[0]);
        if (viewer == null || target == null) {
            return;
        }
        if (viewer.equals(target)) {
            sender.sendMessage("§cDu kannst dein eigenes Inventar bereits sehen.");
            return;
        }
        boolean canEdit = sender.hasPermission("serversystem.command.invsee.edit");
        inventoryViewTargets.put(viewer.getUniqueId(), target.getUniqueId());
        viewer.openInventory(target.getInventory());
        sender.sendMessage(canEdit
                ? "§aDu siehst und bearbeitest das Inventar von " + target.getName() + "."
                : "§eDu siehst das Inventar von " + target.getName() + " schreibgeschützt.");
    }

    private void toggleContainerLock(CommandSender sender, String[] args) {
        if (!require(sender, "serversystem.command.lockchest")) {
            return;
        }
        if (args.length != 0) {
            sender.sendMessage("Verwendung: /lockchest (auf eine Kiste oder ein Barrel schauen)");
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof TileState tile)
                || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST
                && block.getType() != Material.BARREL)) {
            sender.sendMessage("§cSchaue innerhalb von 5 Blöcken auf eine Kiste oder ein Barrel.");
            return;
        }
        if (tile.getPersistentDataContainer().has(lockedContainerKey, PersistentDataType.BYTE)) {
            tile.getPersistentDataContainer().remove(lockedContainerKey);
            tile.update();
            sender.sendMessage("§aDie Kiste ist wieder entsperrt.");
        } else {
            tile.getPersistentDataContainer().set(lockedContainerKey, PersistentDataType.BYTE, (byte) 1);
            tile.update();
            sender.sendMessage("§cDie Kiste wurde gesperrt.");
        }
    }

    private boolean isLocked(Block block) {
        return block.getState() instanceof TileState tile
                && tile.getPersistentDataContainer().has(lockedContainerKey, PersistentDataType.BYTE);
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage("§cDafür hast du keine Berechtigung.");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("§cDieser Befehl kann nur von Spielern verwendet werden.");
        return null;
    }

    private Player findOnlinePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage("§cSpieler nicht gefunden oder nicht online.");
        }
        return target;
    }

    private void showToEveryone(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    private boolean matchesRequest(TeleportRequest request, String[] args) {
        if (request == null) {
            return false;
        }
        if (args.length == 0) {
            return true;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        return requester != null && requester.getName().equalsIgnoreCase(args[0]);
    }

    private boolean isExpired(TeleportRequest request) {
        return System.currentTimeMillis() >= request.expiresAtMillis();
    }

    private void expireRequest(TeleportRequest request) {
        if (requestsByTarget.get(request.recipient()) != request) {
            return;
        }
        removeRequest(request);
        Player requester = Bukkit.getPlayer(request.requester());
        Player recipient = Bukkit.getPlayer(request.recipient());
        if (requester != null) {
            requester.sendMessage("§cDeine Teleportanfrage ist abgelaufen.");
        }
        if (recipient != null) {
            recipient.sendMessage("§eDie Teleportanfrage ist abgelaufen.");
        }
    }

    private void removeRequest(TeleportRequest request) {
        if (request == null) {
            return;
        }
        requestsByTarget.remove(request.recipient(), request);
        requestsByRequester.remove(request.requester(), request);
    }

    private void cancelRequestForDisconnect(Player player) {
        TeleportRequest outgoing = requestsByRequester.remove(player.getUniqueId());
        if (outgoing != null) {
            requestsByTarget.remove(outgoing.recipient(), outgoing);
        }
        TeleportRequest incoming = requestsByTarget.remove(player.getUniqueId());
        if (incoming != null) {
            requestsByRequester.remove(incoming.requester(), incoming);
            Player requester = Bukkit.getPlayer(incoming.requester());
            if (requester != null) {
                requester.sendMessage("§cDie Teleportanfrage wurde abgebrochen, weil der Empfänger offline gegangen ist.");
            }
        }
    }

    private record TeleportRequest(UUID requester, UUID recipient, boolean bringTarget, long expiresAtMillis) {
    }
}