package de.malik.serversystem.economy;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class VaultEconomyHook extends AbstractEconomy {
    private final JavaPlugin plugin;
    private final EconomySystem economy;

    public VaultEconomyHook(JavaPlugin plugin, EconomySystem economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "ServerSystem";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return economy.formatMoney(amount);
    }

    @Override
    public String currencyNamePlural() {
        return plugin.getConfig().getString("economy.currency", "$");
    }

    @Override
    public String currencyNameSingular() {
        return currencyNamePlural();
    }

    @Override
    public boolean hasAccount(String name) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null;
    }

    @Override
    public double getBalance(String name) {
        return getBalance(Bukkit.getOfflinePlayer(name));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) {
            return 0;
        }
        return economy.getBalance(player.getUniqueId());
    }

    @Override
    public boolean has(String name, double amount) {
        return has(Bukkit.getOfflinePlayer(name), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(name), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Spieler nicht gefunden.");
        }
        boolean success = economy.withdraw(player.getUniqueId(), amount);
        double balance = getBalance(player);
        if (success) {
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Nicht genug Guthaben.");
    }

    @Override
    public EconomyResponse depositPlayer(String name, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(name), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Spieler nicht gefunden.");
        }
        boolean success = economy.deposit(player.getUniqueId(), amount);
        double balance = getBalance(player);
        if (success) {
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Einzahlung fehlgeschlagen.");
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String worldName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public EconomyResponse isBankMember(String name, String worldName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banken nicht unterstützt.");
    }

    @Override
    public double getBalance(String name, String worldName) {
        return getBalance(name);
    }

    @Override
    public boolean has(String name, String worldName, double amount) {
        return has(name, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String name, String worldName, double amount) {
        return withdrawPlayer(name, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String name, String worldName, double amount) {
        return depositPlayer(name, amount);
    }

    @Override
    public boolean hasAccount(String name, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String name) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return player != null;
    }

    @Override
    public boolean createPlayerAccount(String name, String worldName) {
        return true;
    }
}