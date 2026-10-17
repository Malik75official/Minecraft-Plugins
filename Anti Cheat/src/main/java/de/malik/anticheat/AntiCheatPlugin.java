package de.malik.anticheat;

import de.malik.anticheat.alerts.AlertManager;
import de.malik.anticheat.checks.combat.CombatCheck;
import de.malik.anticheat.checks.movement.MovementCheck;
import de.malik.anticheat.checks.xray.XrayCheck;
import de.malik.anticheat.commands.AntiCheatCommand;
import de.malik.anticheat.startup.StartupBanner;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AntiCheatPlugin extends JavaPlugin {
    private AlertManager alertManager;
    private MovementCheck movementCheck;
    private XrayCheck xrayCheck;

    @Override
    public void onEnable() {
        StartupBanner.send(getServer().getConsoleSender());
        saveDefaultConfig();
        if (!prepareDataFolder()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        alertManager = new AlertManager(this);
        xrayCheck = new XrayCheck(this);
        registerChecks();
        if (!registerCommand()) {
            return;
        }
        getLogger().info("AntiCheat ist aktiviert. Verdachtsmeldungen werden protokolliert; es gibt keine automatischen Strafen.");
    }

    @Override
    public void onDisable() {
        if (movementCheck != null) {
            movementCheck.shutdown();
        }
        if (xrayCheck != null) {
            xrayCheck.shutdown();
        }
    }

    public void flag(Player player, CheckType check, String detail) {
        if (player.hasPermission(check.getBypassPermission())) {
            return;
        }
        if (check == CheckType.XRAY && xrayCheck != null) {
            xrayCheck.activateOreDecoys(player);
        }
        alertManager.flag(player, check, detail);
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public void reloadAntiCheatConfig() {
        reloadConfig();
        if (xrayCheck != null) {
            xrayCheck.refreshOreDecoys();
        }
    }

    private boolean prepareDataFolder() {
        Path logDirectory = getDataFolder().toPath().resolve("logs");
        try {
            Files.createDirectories(logDirectory);
            return true;
        } catch (IOException exception) {
            getLogger().severe("Konnte das AntiCheat-Logverzeichnis nicht erstellen: " + exception.getMessage());
            return false;
        }
    }

    private void registerChecks() {
        movementCheck = new MovementCheck(this);
        getServer().getPluginManager().registerEvents(movementCheck, this);
        getServer().getPluginManager().registerEvents(new CombatCheck(this), this);
        getServer().getPluginManager().registerEvents(xrayCheck, this);
    }

    private boolean registerCommand() {
        AntiCheatCommand executor = new AntiCheatCommand(this);
        var command = getCommand("anticheat");
        if (command == null) {
            getLogger().severe("Der Befehl 'anticheat' fehlt in plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        return true;
    }
}
