package de.malik.serversystem.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import de.malik.serversystem.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomySystem implements CommandExecutor {
    private final JavaPlugin plugin;
    private final YamlStore playerData;

    public EconomySystem(JavaPlugin plugin, YamlStore playerData) {
        this.plugin = plugin;
        this.playerData = playerData;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "payall" -> payAll(sender, args);
            case "eco" -> eco(sender, args);
            default -> sender.sendMessage("Unbekannter Economy-Befehl.");
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length > 1) {
            sender.sendMessage("Verwendung: /balance [Spieler]");
            return;
        }
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Die Konsole muss einen Spielernamen angeben.");
                return;
            }
            target = player;
        } else {
            boolean self = sender instanceof Player player && player.getName().equalsIgnoreCase(args[0]);
            if (!self && !sender.hasPermission("serversystem.economy.admin")) {
                sender.sendMessage("Dafür hast du keine Berechtigung.");
                return;
            }
            target = Bukkit.getOfflinePlayer(args[0]);
        }
        sender.sendMessage(target.getName() + " hat " + formatMoney(getBalance(target.getUniqueId())) + ".");
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können Geld überweisen.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage("Verwendung: /pay <Spieler> <Betrag>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        double amount = parseAmount(args[1]);
        if (target == null || target.equals(player) || !Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage("Spieler oder Betrag ist ungültig.");
            return;
        }
        double balance = getBalance(player.getUniqueId());
        if (balance < amount) {
            sender.sendMessage("Dein Guthaben reicht dafür nicht aus.");
            return;
        }
        double recipientBalance = addMoney(getBalance(target.getUniqueId()), amount);
        if (!Double.isFinite(recipientBalance)) {
            sender.sendMessage("Die Auszahlung würde das maximale Guthaben überschreiten.");
            return;
        }
        setBalance(player.getUniqueId(), balance - amount);
        setBalance(target.getUniqueId(), recipientBalance);
        sender.sendMessage("Du hast " + formatMoney(amount) + " an " + target.getName() + " überwiesen.");
        target.sendMessage("Du hast " + formatMoney(amount) + " von " + player.getName() + " erhalten.");
    }

    private void payAll(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Verwendung: /payall <Betrag>");
            return;
        }
        double amount = parseAmount(args[0]);
        if (!Double.isFinite(amount) || amount <= 0) {
            sender.sendMessage("Der Betrag muss eine gültige Zahl größer als 0 sein.");
            return;
        }

        Map<UUID, Double> updatedBalances = new LinkedHashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target == null) {
                continue;
            }
            double updated = addMoney(getBalance(target.getUniqueId()), amount);
            if (!Double.isFinite(updated)) {
                sender.sendMessage("Auszahlung abgebrochen: Guthaben von " + target.getName() + " wäre zu groß.");
                return;
            }
            updatedBalances.put(target.getUniqueId(), updated);
        }
        if (updatedBalances.isEmpty()) {
            sender.sendMessage("Es sind keine Spieler online.");
            return;
        }

        for (Map.Entry<UUID, Double> entry : updatedBalances.entrySet()) {
            playerData.data().set("balances." + entry.getKey(), entry.getValue());
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null) {
                target.sendMessage("Du hast " + formatMoney(amount) + " als Server-Auszahlung erhalten.");
            }
        }
        playerData.save();
        sender.sendMessage(formatMoney(amount) + " wurden an " + updatedBalances.size() + " Spieler ausgezahlt.");
    }

    private void eco(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Verwendung: /eco <set|add|take> <Spieler> <Betrag>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount = parseAmount(args[2]);
        if (!Double.isFinite(amount) || amount < 0) {
            sender.sendMessage("Der Betrag muss eine gültige, nicht negative Zahl sein.");
            return;
        }
        double balance = getBalance(target.getUniqueId());
        double updated = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> amount;
            case "add" -> addMoney(balance, amount);
            case "take" -> Math.max(0, subtractMoney(balance, amount));
            default -> -1;
        };
        if (updated < 0 || !Double.isFinite(updated)) {
            sender.sendMessage("Verwendung: /eco <set|add|take> <Spieler> <Betrag>");
            return;
        }
        setBalance(target.getUniqueId(), updated);
        sender.sendMessage("Neues Guthaben für " + target.getName() + ": " + formatMoney(updated) + ".");
    }

    private double getBalance(UUID uuid) {
        String path = "balances." + uuid;
        if (!playerData.data().contains(path)) {
            playerData.data().set(path, plugin.getConfig().getDouble("economy.starting-balance", 100));
        }
        double balance = playerData.data().getDouble(path);
        return Double.isFinite(balance) ? roundMoney(balance) : 0;
    }

    private void setBalance(UUID uuid, double balance) {
        if (!Double.isFinite(balance)) {
            return;
        }
        playerData.data().set("balances." + uuid, roundMoney(balance));
        playerData.save();
    }

    private String formatMoney(double amount) {
        return String.format(Locale.ROOT, "%.2f%s", roundMoney(amount), plugin.getConfig().getString("economy.currency", "$"));
    }

    public String getFormattedBalance(UUID playerId) {
        return formatMoney(getBalance(playerId));
    }

    private double parseAmount(String value) {
        try {
            double amount = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY).doubleValue();
            return Double.isFinite(amount) ? amount : Double.NaN;
        } catch (ArithmeticException | NumberFormatException exception) {
            return Double.NaN;
        }
    }

    private double roundMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double addMoney(double first, double second) {
        return BigDecimal.valueOf(first).add(BigDecimal.valueOf(second))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double subtractMoney(double first, double second) {
        return BigDecimal.valueOf(first).subtract(BigDecimal.valueOf(second))
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}