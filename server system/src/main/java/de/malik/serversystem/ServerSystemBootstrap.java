package de.malik.serversystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

import de.malik.serversystem.bank.BankSystem;
import de.malik.serversystem.caseopening.CaseAdminMenu;
import de.malik.serversystem.caseopening.CaseCommand;
import de.malik.serversystem.caseopening.CaseListener;
import de.malik.serversystem.caseopening.CaseOpeningManager;
import de.malik.serversystem.caseopening.CrystalManager;
import de.malik.serversystem.economy.EconomySystem;
import de.malik.serversystem.inventory.InventorySyncSystem;
import de.malik.serversystem.links.LinkCommandSystem;
import de.malik.serversystem.mob.MobDisplayManager;
import de.malik.serversystem.mob.MobSpawnCommand;
import de.malik.serversystem.moderation.ModerationSystem;
import de.malik.serversystem.prefix.PrefixSystem;
import de.malik.serversystem.sidebar.SidebarSystem;
import de.malik.serversystem.spawn.SpawnSystem;
import de.malik.serversystem.storage.PersistentYamlStorage;
import de.malik.serversystem.storage.YamlStore;
import de.malik.serversystem.teleport.FarmweltSystem;
import de.malik.serversystem.teleport.HomeWarpSystem;
import de.malik.serversystem.trade.TradeSystem;
import de.malik.serversystem.utility.PlayerAdministrationSystem;
import de.malik.serversystem.utility.UtilitySystem;

public final class ServerSystemBootstrap extends JavaPlugin {
    private YamlStore playerData;
    private YamlStore warpData;
    private YamlStore moderationData;
    private YamlStore spawnData;
    private PersistentYamlStorage persistentStorage;
    private PrefixSystem prefixSystem;
    private SidebarSystem sidebarSystem;
    private InventorySyncSystem inventorySyncSystem;
    private CaseOpeningManager caseOpeningManager;
    private CaseAdminMenu caseAdminMenu;
    private CrystalManager crystalManager;
    private PlayerAdministrationSystem playerAdministrationSystem;
    private BankSystem bankSystem;
    private MobDisplayManager mobDisplayManager;

    @Override
    public void onEnable() {
        printStartupBanner();
        saveDefaultConfig();

        persistentStorage = new PersistentYamlStorage(this);
        try {
            persistentStorage.initialize();
        } catch (java.sql.SQLException exception) {
            getLogger().severe("Der konfigurierte MySQL-Datenspeicher konnte nicht gestartet werden: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        inventorySyncSystem = new InventorySyncSystem(this);
        if (inventorySyncSystem.isEnabled()) {
            try {
                inventorySyncSystem.initialize();
            } catch (java.sql.SQLException exception) {
                getLogger().severe("MySQL-Inventarsynchronisierung konnte nicht gestartet werden: " + exception.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        playerData = new YamlStore(this, "players.yml", persistentStorage);
        warpData = new YamlStore(this, "warps.yml", persistentStorage);
        moderationData = new YamlStore(this, "moderation.yml", persistentStorage);
        spawnData = new YamlStore(this, "spawn.yml", persistentStorage);
        FileConfiguration mobConfig = loadMobConfig();
        mobDisplayManager = new MobDisplayManager(this, mobConfig);
        if (!spawnData.data().contains("spawn.location") && getConfig().contains("spawn.location")) {
            spawnData.data().set("spawn.location", getConfig().get("spawn.location"));
            spawnData.save();
        }

        EconomySystem economy = new EconomySystem(this, playerData);
        TradeSystem tradeSystem = new TradeSystem(this, economy, crystalManager);
        try {
            if (getServer().getPluginManager().getPlugin("Vault") != null) {
                Class<?> hookClass = Class.forName("de.malik.serversystem.economy.VaultEconomyHook");
                Object hook = hookClass.getConstructor(JavaPlugin.class, EconomySystem.class).newInstance(this, economy);
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                getServer().getServicesManager().register((Class) economyClass, hook, this, ServicePriority.Highest);
                getLogger().info("Vault-Economy-Provider registriert.");
            }
        } catch (Throwable throwable) {
            getLogger().warning("Vault-Economy-Provider konnte nicht registriert werden: " + throwable.getMessage());
        }
        bankSystem = new BankSystem(this, economy, persistentStorage);
        caseOpeningManager = new CaseOpeningManager(this, economy, persistentStorage);
        caseAdminMenu = new CaseAdminMenu(this);
        crystalManager = new CrystalManager(this, persistentStorage);
        CaseCommand caseCommand = new CaseCommand(this, caseAdminMenu);
        registerCommands(caseCommand, "case", "case-set", "case-clear", "kristall", "crystals");
        registerEvents(new CaseListener(this), caseAdminMenu);

        HomeWarpSystem homeWarps = new HomeWarpSystem(playerData, warpData);
        FarmweltSystem farmwelt = new FarmweltSystem(this);
        SpawnSystem spawn = new SpawnSystem(this, spawnData);
        ModerationSystem moderation = new ModerationSystem(moderationData);
        prefixSystem = new PrefixSystem(this, persistentStorage);
        sidebarSystem = new SidebarSystem(this, economy, prefixSystem, persistentStorage);
        UtilitySystem utilities = new UtilitySystem();
        playerAdministrationSystem = new PlayerAdministrationSystem(this);
        LinkCommandSystem linkCommands = new LinkCommandSystem(this);
        MobSpawnCommand mobSpawnCommand = new MobSpawnCommand(mobConfig, mobDisplayManager);

        registerCommands(economy, "balance", "pay", "payall", "eco");
        registerCommands(tradeSystem, "trade");
        registerEvents(tradeSystem);
        registerCommands(bankSystem, "bank", "banknpc");
        registerCommands(homeWarps, "sethome", "home", "delhome", "homes", "setwarp", "warp", "delwarp");
        registerCommands(farmwelt, "farmwelt");
        registerCommands(spawn, "setspawn", "spawn");
        registerCommands(utilities, "ec", "craft", "amboss", "ofen");
        registerCommands(playerAdministrationSystem, "fly", "i", "vanish", "gamemode", "tp", "tpo", "tphere",
            "tpa", "tpahere", "tpaccept", "tpadeny", "tpacancel", "invsee", "lockchest");
        registerCommands(linkCommands, "discord", "webseite", "webshop");
        registerCommands(moderation, "kick", "ban", "unban", "mute", "unmute");
        registerCommands(mobSpawnCommand, "mobspawn");
        PluginCommand mobSpawnPluginCommand = getCommand("mobspawn");
        if (mobSpawnPluginCommand != null) {
            mobSpawnPluginCommand.setTabCompleter(mobSpawnCommand);
        }

        registerEvents(spawn, moderation, prefixSystem, sidebarSystem, playerAdministrationSystem, bankSystem);
        registerEvents(mobDisplayManager);
        mobDisplayManager.start();
        if (inventorySyncSystem.isEnabled()) {
            registerEvents(inventorySyncSystem);
        }
        prefixSystem.start();
        sidebarSystem.start();
        inventorySyncSystem.start();
    }

    private void printStartupBanner() {
        String[] banner = {
                "",
            "BBBBB   Y   Y       M   M   AAAAA   L       I  K   K   77777  55555",
            "B    B   Y Y        MM MM   A   A   L       I  K  K       7    5",
            "BBBBB     Y         M M M   AAAAA   L       I  KKK       7    5555",
            "B    B    Y         M   M   A   A   L       I  K  K     7        5",
            "BBBBB     Y         M   M   A   A   LLLLL   I  K   K   77777  5555",
                ""
        };
        for (String line : banner) {
            getLogger().info(line);
        }
    }

    @Override
    public void onDisable() {
        if (mobDisplayManager != null) {
            mobDisplayManager.shutdown();
        }
        if (inventorySyncSystem != null) {
            inventorySyncSystem.shutdown();
        }
        if (sidebarSystem != null) {
            sidebarSystem.shutdown();
        }
        if (prefixSystem != null) {
            prefixSystem.shutdown();
        }
        if (playerAdministrationSystem != null) {
            playerAdministrationSystem.shutdown();
        }
        if (bankSystem != null) {
            bankSystem.shutdown();
        }
        if (crystalManager != null) {
            crystalManager.save();
        }
        save(playerData);
        save(warpData);
        save(moderationData);
        save(spawnData);
    }

    private void registerCommands(CommandExecutor executor, String... names) {
        for (String name : names) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                getLogger().severe("Command '" + name + "' fehlt in plugin.yml.");
                continue;
            }
            command.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                command.setTabCompleter(tabCompleter);
            }
        }
    }

    private void registerEvents(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void save(YamlStore store) {
        if (store != null) {
            store.save();
        }
    }

    private FileConfiguration loadMobConfig() {
        File mobConfigFile = new File(getDataFolder(), "mobs.yml");
        if (!mobConfigFile.exists()) {
            File legacyConfig = new File(getDataFolder().getParentFile(), "MobSystem/config.yml");
            if (legacyConfig.isFile()) {
                try {
                    Files.copy(legacyConfig.toPath(), mobConfigFile.toPath());
                    getLogger().info("Mob-Einstellungen aus plugins/MobSystem/config.yml wurden übernommen.");
                } catch (IOException exception) {
                    getLogger().warning("Alte Mob-Einstellungen konnten nicht übernommen werden: " + exception.getMessage());
                }
            }
            if (!mobConfigFile.exists()) {
                saveResource("mobs.yml", false);
            }
        }

        FileConfiguration mobConfig = YamlConfiguration.loadConfiguration(mobConfigFile);
        try (InputStreamReader defaults = new InputStreamReader(getResource("mobs.yml"), StandardCharsets.UTF_8)) {
            mobConfig.addDefaults(YamlConfiguration.loadConfiguration(defaults));
            mobConfig.options().copyDefaults(true);
            mobConfig.save(mobConfigFile);
        } catch (IOException exception) {
            getLogger().warning("Mob-Einstellungen konnten nicht gespeichert werden: " + exception.getMessage());
        }
        return mobConfig;
    }

    public CaseOpeningManager getCaseOpeningManager() {
        return caseOpeningManager;
    }

    public CrystalManager getCrystalManager() {
        return crystalManager;
    }
}